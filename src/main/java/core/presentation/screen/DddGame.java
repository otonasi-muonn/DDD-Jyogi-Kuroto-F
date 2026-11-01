package core.presentation.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import core.application.GameContext;
import core.application.RunId;
import core.application.RunSession;
import core.application.TurnDirector;
import core.domain.battle.TurnEngine.StepResult;
import core.domain.card.Card;
import core.domain.card.CardId;
import core.domain.card.CardPileState;
import core.domain.card.DrawPile;
import core.domain.dungeon.DungeonState;
import core.domain.entity.EnemyKind;
import core.domain.entity.Player;
import core.domain.equipment.Equipment;
import core.domain.equipment.EquipmentSlot;
import core.domain.layer.LayerEndNode;
import core.domain.meta.PlayerProgress;
import core.domain.meta.Soul;
import core.domain.save.SoulSettlement;
import core.domain.tree.NodeId;
import core.domain.tree.SoulTree;
import core.infrastructure.audio.SoundManager;
import core.infrastructure.bootstrap.CardImageRegistry;
import core.infrastructure.bootstrap.InitialStateFactory;
import core.infrastructure.save.PersistenceServices;
import core.infrastructure.save.ProfileData;
import core.infrastructure.save.ProfileDataMapper;
import core.infrastructure.save.RunCheckpoint;
import core.infrastructure.save.RunCheckpointMapper;
import core.infrastructure.save.RunLifecycle;
import core.infrastructure.save.SaveData;
import core.infrastructure.save.SaveDataConverter;
import core.infrastructure.save.Settings;
import core.presentation.render.Fonts;
import core.presentation.render.GameResources;
import core.presentation.render.Strings;
import core.presentation.render.UiTheme;
import core.presentation.render.UiThemeResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

/**
 * LibGDX の {@link Game} を継承したエントリポイント。
 *
 * <p>presentation 層に置く理由: LibGDX 依存 (Game 継承 + Screen 切替) を application 層から 切り離すため。application 層は
 * LibGDX に依存しない {@link GameContext} と {@link TurnDirector} のみで構成される。
 *
 * <p>{@link GameContext} / {@link TurnDirector} / {@link Fonts} を 1 つの所有者 (この Game) に集約する
 * ことで、Screen 側が古い参照を保持して新ラン後に状態を取りこぼす事故を構造的に防ぐ。Screen からは {@link #context()} / {@link #director()}
 * / {@link #fonts()} で都度取得する。
 */
public final class DddGame extends Game {

  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(DddGame.class.getName());

  /**
   * ラン終了処理の結果。画面側が「進んでよいか」を判断するために使う。
   *
   * <p>{@link #ALREADY_SETTLED} と {@link #SAVE_FAILED} を区別することが要点。どちらも「精算が走らなかった」 点は同じだが、前者は Screen
   * ライフサイクルによる再入で正常、後者は再試行が必要な失敗である。 active な RunSession の有無だけで雑に扱うと、この 2 つが混ざる。
   */
  public enum RunEndResult {
    /** 精算して永続化まで成功した。 */
    SETTLED,
    /** 既に精算済み (show() の再入など)。進んでよい。 */
    ALREADY_SETTLED,
    /** 永続化に失敗した。RunSession を保持したままなので再試行できる。 */
    SAVE_FAILED,
    /** Checkpoint / Soul が不正で精算できない。進ませてはならない。 */
    INVALID_STATE
  }

  /**
   * ラン単位の application 層状態の集約 (Wave 9 W9-α、{@link RunSession} record)。
   *
   * <p>従来 3 個別フィールド (context / director / runRng) として保持していたものを 1 つの Optional に置換。 ラン未開始の意味を {@code
   * Optional.empty()} で型表現することで、{@code if (context == null) return} 等の null チェック散在を撤廃する。
   *
   * <p>Screen 側からのアクセスは {@link #requireRunSession()} を推奨 ({@link IllegalStateException} を含み、
   * ラン未開始時の NoSuchElementException 即死を回避してデバッグ性を高める)。
   */
  private Optional<RunSession> runSession = Optional.empty();

  /**
   * LibGDX リソース系 3 コンポーネント (Fonts / SoundManager / CardImageRegistry) の集約 (Wave 8 W8-β)。
   *
   * <p>create() で {@link GameResources#load} により一括初期化、dispose() で防衛的解放。 旧 3 個別フィールドへの直接参照は中継 getter
   * ({@link #fonts()} 等) 経由に置換済。
   */
  private GameResources resources;

  /**
   * ラン外で持続するプレイヤー進捗を集約した不変 record (§15-2/-5/-7/-9/-10/-11、Wave 6 W6-γ で導入)。
   *
   * <p>従来 DddGame に散在していた 7 個別フィールド (soulTree / playerSoul / tutorialSeen / runCount /
   * obtainedCards / bestiary / loadout) を 1 集約に置換。各 setter は {@code progress =
   * progress.withXxx(...)} で新インスタンスを返す純関数 + 代入で更新する (Escape Analysis でチェイン生成は GC 負荷なし)。
   *
   * <p>Wave 7 W7-β で {@link #progress()} を公開し、旧 7 中継 getter (playerSoul / runCount / soulTree /
   * bestiary / loadout / obtainedCards / isTutorialSeen) は撤去済。Screen 側は {@code
   * game.progress().playerSoul()} 等で field にアクセスする。
   */
  private PlayerProgress progress = PlayerProgress.initial(defaultLoadout());

  /**
   * 永続化サービス 3 コンポーネント (SaveManager / SettingsManager / Settings) の集約 (Wave 9 W9-β)。
   *
   * <p>create() で {@link PersistenceServices#load} により一括初期化。applySettings() / saveSettings() 等の
   * 操作は本 holder を経由する。LibGDX 副作用 (フルスクリーン / 音量) は本 holder に含めず、 DddGame 側で仲介する (CTO レビュー反映
   * #3、infrastructure 層の純粋性維持)。
   */
  private PersistenceServices persistence;

  private static Map<EquipmentSlot, Equipment> defaultLoadout() {
    Equipment dagger = InitialStateFactory.tatteredDagger();
    Map<EquipmentSlot, Equipment> m = new HashMap<>();
    m.put(dagger.slot(), dagger);
    return m;
  }

  /**
   * 現在のラン状態を返す (Wave 9 W9-α、ラン未開始時は {@link Optional#empty()})。内部利用 (saveAtLayerBoundary 等)
   * で「ラン未開始ならスキップ」を表現するときに使う。Screen 側は通常 {@link #requireRunSession()} を使うこと。
   */
  public Optional<RunSession> runSession() {
    return runSession;
  }

  /**
   * 現在のラン状態を返す。ラン未開始時は {@link IllegalStateException} を投げる (Wave 9 W9-α、CTO レビュー反映 #2)。
   *
   * <p>Screen 側から `game.requireRunSession().context()` 等で参照する。`.get()` で NoSuchElementException 即死を
   * 回避し、IllegalStateException + 明示メッセージでデバッグ性を高める。
   */
  public RunSession requireRunSession() {
    return runSession.orElseThrow(() -> new IllegalStateException("Run session not active"));
  }

  /**
   * ラン外進捗 record を返す (Wave 7 W7-β で公開化)。Screen 側からは {@code game.progress().playerSoul()} 等で
   * フィールドにアクセスする。本メソッド経由でアクセスを集約することで、DddGame の API 表面積を縮小する (旧中継 getter 7 件を撤去)。
   */
  public PlayerProgress progress() {
    return progress;
  }

  /**
   * 現在の Settings.themeMode (LIGHT / DARK) から UI テーマを動的に決定する (Wave 17 W17-β / #8、旧装備依存テーマ廃止)。
   *
   * <p>毎フレーム呼ばれる前提 → SettingsScreen で themeMode を切替えると即座に UI が明暗反転する (CTO #3 リアルタイム反映)。
   */
  public UiTheme activeUiTheme() {
    return UiThemeResolver.resolve(persistence.settings().themeMode());
  }

  private void saveProgressProfile() {
    if (persistence != null && persistence.saveManager() != null) {
      ProfileData previous = persistence.runLifecycle().profileOrInitial();
      // メタ保存では恒久ソウル (previous.soulTotal()) を据え置く。
      // progress.playerSoul() はラン中 0 のため、forSoulUpdate で書くと持越しソウルが 0 で全損する。
      ProfileData profile = ProfileDataMapper.forLayerBoundary(progress, previous);
      persistence.saveManager().saveProfile(profile);
    }
  }

  /** ラン外メタ進行 (ソウルツリー解放・リセット) で変更された playerSoul を Profile に即時永続化する。 */
  private void saveProgressProfileWithSoul() {
    if (persistence != null && persistence.saveManager() != null) {
      ProfileData previous = persistence.runLifecycle().profileOrInitial();
      ProfileData profile =
          ProfileDataMapper.forSoulUpdate(progress, previous, progress.playerSoul().amount());
      persistence.saveManager().saveProfile(profile);
    }
  }

  /** 装備をそのスロットに装着する (同スロットの既存装備は置き換え、§15-9、次ラン開始時に反映)。 */
  public void equipInLoadout(Equipment equipment) {
    Objects.requireNonNull(equipment, "equipment");
    if (!progress.equipmentOwnership().ownedIds().contains(equipment.id())) {
      return; // 未所有装備は装着不可 (EQUIP-02)
    }
    Map<EquipmentSlot, Equipment> next = new HashMap<>(progress.loadout());
    next.put(equipment.slot(), equipment);
    progress = progress.withLoadout(next);
    saveProgressProfile();
  }

  /**
   * 指定スロットの装備を外す (§15-9、次ラン開始時に反映)。
   *
   * <p>ロードアウトを空にはできない (最後の 1 個は外せない)。空ロードアウト → 空デッキでラン開始すると 攻撃手段ゼロで詰むため。
   */
  public void unequipSlot(EquipmentSlot slot) {
    Objects.requireNonNull(slot, "slot");
    if (progress.loadout().size() <= 1) {
      return; // 最後の装備は外さない (空デッキ防止)
    }
    Map<EquipmentSlot, Equipment> next = new HashMap<>(progress.loadout());
    next.remove(slot);
    progress = progress.withLoadout(next);
    saveProgressProfile();
  }

  /**
   * 敵を撃破したことを Bestiary に記録する (§15-5 / E-7、DungeonScreen の ActorDied ハンドラから呼ぶ)。
   *
   * @param kind 撃破した敵の種別
   */
  public void recordEnemyDefeated(EnemyKind kind) {
    progress = progress.withBestiary(progress.bestiary().withDefeated(kind));
    saveProgressProfile();
  }

  /** 現在の Player が保持する全カード (山札 + 手札 + 捨て札) の ID を入手済として記録する。 */
  private void recordObtainedCards() {
    if (runSession.isEmpty()) {
      return;
    }
    CardPileState piles = runSession.get().context().state().player().cardPileState();
    Set<CardId> next = new HashSet<>(progress.obtainedCards());
    for (Card c : piles.drawPile().cards()) {
      next.add(c.id());
    }
    for (Card c : piles.hand().cards()) {
      next.add(c.id());
    }
    for (Card c : piles.discardPile().cards()) {
      next.add(c.id());
    }
    progress = progress.withObtainedCards(next);
  }

  public Fonts fonts() {
    return resources.fonts();
  }

  /** サウンドマネージャ (BGM / SE 再生の窓口)。 */
  public SoundManager soundManager() {
    return resources.soundManager();
  }

  /** カード画像レジストリ (CardCollectionScreen / HudRenderer から参照)。 */
  public CardImageRegistry cardImageRegistry() {
    return resources.cardImageRegistry();
  }

  /**
   * カードマスタ (§15-3)。presentation 層が {@code InitialStateFactory.cardCatalog()} を直接呼ぶと presentation →
   * infrastructure 依存方向違反になるため、DddGame 経由で間接化する窓口。
   */
  public core.infrastructure.bootstrap.CardCatalog cardCatalog() {
    return core.infrastructure.bootstrap.InitialStateFactory.cardCatalog();
  }

  /**
   * 装備マスタ (§15-9)。{@link #cardCatalog()} と同型の窓口 (Wave 3 Task A で追加)。 NodeResolveContext 組み立てや装備画面が
   * presentation → infrastructure の直接参照を避けるために使う。
   */
  public core.infrastructure.bootstrap.EquipmentCatalog equipmentCatalog() {
    return core.infrastructure.bootstrap.InitialStateFactory.equipmentCatalog();
  }

  /**
   * 層末ノード解決用の {@link core.domain.layer.NodeResolveContext} を組み立てて返す (Wave 3 Task A)。 cards /
   * equipments の resolver はマスタ {@code get} method reference で構成、ドメイン層は Function のみ参照する。
   */
  public core.domain.layer.NodeResolveContext nodeResolveContext() {
    return new core.domain.layer.NodeResolveContext(
        id -> cardCatalog().get(id), id -> equipmentCatalog().get(id));
  }

  /** §15-10 / E-10: チュートリアル overlay を閉じた時に呼び、次回以降の自動表示を抑制する。 */
  public void markTutorialSeen() {
    progress = progress.withTutorialSeen(true);
    saveProgressProfile();
  }

  /** ラン外のソウルツリー画面でノード解放した結果を受け取る (§15-7)。 */
  public void unlockTreeNode(NodeId nodeId) {
    SoulTree.UnlockResult result = progress.soulTree().unlock(nodeId, progress.playerSoul());
    progress = progress.withSoulTree(result.newTree()).withPlayerSoul(result.newSoul());
    saveProgressProfileWithSoul();
    // Wave 10 W10-β-2: ノード解放成功時に STATUS_UP SE (旧 LEVEL_UP からユーザー判断で変更、体感整合)
    if (resources != null) {
      resources.soundManager().playSe(core.infrastructure.audio.SeKind.STATUS_UP);
    }
  }

  /** ラン外のソウルツリー画面でリセットボタン押下時に呼ぶ (ADR-09)。 */
  public void resetTree() {
    SoulTree.ResetResult result = progress.soulTree().reset();
    progress =
        progress
            .withSoulTree(result.newTree())
            .withPlayerSoul(progress.playerSoul().add(result.refundedSoul()));
    saveProgressProfileWithSoul();
  }

  /** ラン終了時 (GameOverScreen 表示時) に Player.soul をラン外保持に書き戻す (§15-7)。 */
  public void preserveSoulFromRun() {
    runSession.ifPresent(
        session -> progress = progress.withPlayerSoul(session.context().state().player().soul()));
  }

  /**
   * ラン終了時 (GameOverScreen 表示時) に呼ぶ (§15-7 / E-2)。{@link #preserveSoulFromRun()} で 獲得ソウルをラン外保持に退避し、
   * runCount を 1 増やす。runCount が 1 以上になることで 以降ソウルツリー動線 (タイトルの T キー) が解禁される。
   *
   * <p>Wave 9 W9-α CTO レビュー反映 #1: ラン揮発状態を {@link Optional#empty()} で明示クリアし、前回ランの GameContext が
   * 次回ランにリバウンドする事故を構造的に防ぐ。Android (Phase D) のメモリ圧迫回避にも寄与。
   */
  public RunEndResult onRunEnded() {
    // 二重精算の防止。GameOverScreen.show() は Screen ライフサイクルで再度呼ばれうるため、
    // active な RunSession が無い呼び出しは「既に精算済み」とみなして何もしない。
    // 旧実装はセッション無しでも精算を続行しており、2 回目は initialRunSoul=0 で
    // delta = 恒久総額そのものになるため、Soul と runCount が二重加算されていた。
    if (runSession.isEmpty()) {
      LOG.warning("ラン終了処理が RunSession 無しで呼ばれました。精算済みとみなしてスキップします。");
      return RunEndResult.ALREADY_SETTLED;
    }
    RunSession session = runSession.get();

    RunLifecycle lifecycle = persistence.runLifecycle();
    ProfileData previous = lifecycle.profileOrInitial();

    // 計算・検証は progress を書き換える前に行う。保存に失敗したときメモリだけ進むのを防ぐ。
    int finalSoul = session.context().state().player().soul().amount();
    OptionalInt optSettledTotal =
        SoulSettlement.settle(previous.soulTotal(), finalSoul, session.initialRunSoul());
    if (optSettledTotal.isEmpty()) {
      LOG.severe("ラン終了時: 異常な Checkpoint / Soul 状態のため精算を中止し、Profile / Checkpoint を保護しました。");
      return RunEndResult.INVALID_STATE;
    }
    int settledSoulTotal = optSettledTotal.getAsInt();

    PlayerProgress nextProgress =
        progress.withRunCount(progress.runCount() + 1).withPlayerSoul(new Soul(settledSoulTotal));
    ProfileData settled =
        ProfileDataMapper.forSoulUpdate(nextProgress, previous, settledSoulTotal)
            .withSettledRunId(session.runId().value());

    if (!lifecycle.endRun(settled).isSuccess()) {
      LOG.severe("ラン終了時の保存に失敗しました。Checkpoint は削除せず、メモリ上の進捗も変更しません。");
      // RunSession は保持したままにする。呼出側が再試行できるようにするため。
      return RunEndResult.SAVE_FAILED;
    }

    // 永続化が成功したときだけメモリへ反映する。
    progress = nextProgress;
    runSession = Optional.empty();
    return RunEndResult.SETTLED;
  }

  /**
   * 進行中ランを放棄して精算する (SAVE-03B / §15-9)。
   *
   * <p>放棄は死亡と同じ扱いにする。§15-9 は「敵撃破とイベントでラン中に得た Soul は、死亡・放棄・ クリアのいずれでも 100% を Profile
   * へ移す」と定めるため、Checkpoint に記録された総ソウルを 恒久側へ書き戻してから Checkpoint を削除する。
   *
   * <p>再開可能な Checkpoint が無い場合は何もしない。
   *
   * @return 放棄して精算した場合 true
   */
  public boolean abandonActiveRun() {
    RunLifecycle lifecycle = persistence.runLifecycle();
    Optional<RunCheckpoint> optCheckpoint = lifecycle.resumableCheckpoint();
    if (optCheckpoint.isEmpty()) {
      return false;
    }
    RunCheckpoint checkpoint = optCheckpoint.get();

    ProfileData previous = lifecycle.profileOrInitial();
    int finalSoul = checkpoint.currentRunSoul();
    int initialSoul = checkpoint.initialRunSoul();
    OptionalInt optSettledTotal =
        SoulSettlement.settle(previous.soulTotal(), finalSoul, initialSoul);
    if (optSettledTotal.isEmpty()) {
      LOG.severe("ラン放棄時: 異常な Checkpoint のため精算を中止し、データを保護しました。");
      return false;
    }
    int settledSoulTotal = optSettledTotal.getAsInt();

    // 計算・検証 → nextProgress → 永続化成功 → メモリ反映 の順を守る。
    // 旧実装は endRun の前に progress を書き換えており、保存失敗時にメモリだけ進んでいた。
    PlayerProgress nextProgress =
        progress.withPlayerSoul(new Soul(settledSoulTotal)).withRunCount(progress.runCount() + 1);

    ProfileData settled =
        ProfileDataMapper.forSoulUpdate(nextProgress, previous, settledSoulTotal)
            .withSettledRunId(checkpoint.runId());
    if (!lifecycle.endRun(settled).isSuccess()) {
      LOG.severe("ラン放棄の精算に失敗しました。Checkpoint は削除せず、メモリ上の進捗も変更しません。");
      return false;
    }

    progress = nextProgress;
    runSession = Optional.empty();
    return true;
  }

  /**
   * 来場者交代用にセーブデータ (恒久進捗・進行中ラン) をすべて削除し、初期状態へ戻す。
   *
   * <p>{@code settings.json} (音量・フルスクリーン・UI プリセット・テーマ) は展示機材の設定として残す。
   * 全消去のため精算・ソウル移行は行わない。削除に失敗した場合はメモリ上の {@code progress} / {@code runSession} を変更せず false を返す。
   *
   * @return 初期化に成功した場合 true
   */
  public boolean resetAllProgress() {
    SaveManager.SaveResult result = persistence.saveManager().deleteAllProgress();
    if (!result.isSuccess()) {
      LOG.severe("セーブデータの初期化に失敗しました: " + result.message());
      return false;
    }
    runSession = Optional.empty();
    progress =
        ProfileDataMapper.toPlayerProgress(
            persistence.runLifecycle().profileOrInitial(), defaultLoadout());
    return true;
  }

  /**
   * ダンジョン内の一時停止メニューからランを中断してタイトルへ戻る際に呼ぶ。
   *
   * <p>ファイルの書込・削除は一切行わず、メモリ上の {@code runSession} を破棄し、{@code progress} を {@link #create()}
   * と同じ方法でディスクの {@code profile.json} から読み直す。
   *
   * <p>理由: ラン開始時に {@code progress.playerSoul} は 0 にされて {@code Player} 側へ移っている。{@code RunSession}
   * だけ捨ててメモリの {@code progress} を使い続けると、次の新規ランで持越しソウル 0 のまま Profile
   * を上書きしてソウルを失う。ディスクから読み直せば「ゲームを再起動したのと同じ状態」になり、層境界セーブがあればタイトルの [L] つづきから で直前の層の始めから再開できる。
   */
  public void suspendRunToTitle() {
    runSession = Optional.empty();
    progress =
        ProfileDataMapper.toPlayerProgress(
            persistence.runLifecycle().profileOrInitial(), defaultLoadout());
  }

  /** 新しいラン (= ダンジョン挑戦) を開始する。RunSession を作り直す。 */
  public void startNewRun() {
    // ADR-19: Random は引数注入で再現性を呼出元に委ねる (初期手札シャッフル + 毎ターンドロー)。
    // ラン毎に new Random() で異なるシード = 本番プレイは非再現的 (テストでは固定シードを渡す)。
    Random newRng = new Random();
    // §15-6 / SoulTree LayerExtend: 解放済みの LayerExtendEffect の合計値からラン最大層数を算出。
    // generateLayerState がボス配置のために最大層数を必要とするため、firstFloor 前に計算する。
    int extendAmount = totalLayerExtendAmount();
    int maxLayer = InitialStateFactory.DEFAULT_MAX_LAYER + extendAmount;
    DungeonState state = InitialStateFactory.firstFloor(newRng, progress.loadout(), maxLayer);
    // §15-7: ソウルツリーの解放済み効果を Player に適用 (素ステ補正 / カード追加 / 枠拡張)
    // LayerExtendEffect は Player に副作用がないため、ループは no-op (副作用は GameContext.maxLayer 側で集約済)。
    Player applied = progress.soulTree().applyTo(state.player(), InitialStateFactory::resolveCard);
    // §15-2 / §15-7: ラン外のソウル保持を Player に注入 (前回ランからの持ち越し)
    // SAVE-03B: 注入前の値を控える。progress を 0 化した後に Profile を書くと、
    // 保有ソウルが 0 で潰れて中断離脱時に全損する。
    Soul carriedOverSoul = progress.playerSoul();
    Player withSoul = applied.addSoul(carriedOverSoul);
    // 注入後は外部保持を 0 に (重複加算防止、preserveSoulFromRun でラン終了時に書き戻る)
    progress = progress.withPlayerSoul(Soul.zero());
    GameContext newContext = GameContext.startNewRun(state.withPlayer(withSoul));
    if (extendAmount > 0) {
      newContext.extendMaxLayer(extendAmount);
    }
    TurnDirector newDirector = new TurnDirector(newContext, newRng);
    // SAVE-03A: 新規ランへ一意な ID を割り当てる。Profile.activeRunId と突き合わせることで
    // 終了済みランの Checkpoint からの再開と二重精算を拒否できる (レビュー P0-1)。
    RunId runId = RunId.newRandom();
    runSession =
        Optional.of(
            new RunSession(runId, newContext, newDirector, newRng, carriedOverSoul.amount()));
    recordObtainedCards(); // §15-3: 初期デッキを図鑑に記録

    // SAVE-03B: Profile の activeRunId をこのランへ向け、古い Checkpoint を消す。
    // これを飛ばすと activeRunId が前ランを指したままになり、次回起動で放棄したはずの
    // ランが「つづき」として提示される。
    // 注入前の持越しソウルを書くのは、progress 側が既に 0 化されているため
    // (層境界セーブと同じ理由。詳細は ProfileDataMapper の javadoc)。
    RunLifecycle lifecycle = persistence.runLifecycle();
    ProfileData previous = lifecycle.profileOrInitial();
    ProfileData started =
        ProfileDataMapper.forSoulUpdate(progress, previous, carriedOverSoul.amount());
    if (!lifecycle.beginRun(started, runId).isSuccess()) {
      LOG.severe("ラン開始の記録に失敗しました。所持ソウルを復元し、次の層境界セーブで再試行します。");
      progress = progress.withPlayerSoul(carriedOverSoul);
    }
  }

  /**
   * 解放済みノードの {@link core.domain.tree.NodeEffect.LayerExtendEffect} の amountToAdd 合計を返す (SoulTree →
   * GameContext.maxLayer 反映用ヘルパ、Task B)。
   */
  private int totalLayerExtendAmount() {
    int total = 0;
    for (NodeId id : progress.soulTree().unlockedNodes()) {
      core.domain.tree.TreeNode node = SoulTree.allNodes().get(id);
      if (node == null) {
        continue;
      }
      if (node.effect() instanceof core.domain.tree.NodeEffect.LayerExtendEffect le) {
        total += le.amountToAdd();
      }
    }
    return total;
  }

  /**
   * 層末ノードを 1 つ選択 → 効果適用 → 次層へ進む (§15-8 / E-6)。
   *
   * <p>処理:
   *
   * <ol>
   *   <li>{@link LayerEndNode#apply(Player, core.domain.layer.NodeResolveContext)} で Player を強化
   *       (純関数)
   *   <li>強化済の Player を含む新 state を {@link InitialStateFactory#advanceLayer} に渡し、次層 state を生成
   *   <li>{@link TurnDirector#advanceFloor} に委譲し、AP リフィル + 1 枚ドロー + イベント発火
   * </ol>
   *
   * <p>CLEARED 以外の状態で呼ばれた場合、効果は player に適用されるが {@link TurnDirector#advanceFloor} の no-op
   * ガードで層遷移は起きない (二重ガード)。 ただし通常は DungeonScreen 側で CLEARED 時にのみ呼ばれるよう制御する。
   */
  public void resolveLayerEndChoice(LayerEndNode choice) {
    Objects.requireNonNull(choice, "choice");
    RunSession session = requireRunSession();
    DungeonState current = session.context().state();
    Player before = current.player();
    // Wave 3 Task A: Shop は CardId 保持なので cards / equipments resolver を context 経由で渡す
    Player upgraded = choice.apply(before, nodeResolveContext());
    // Wave 3 Task B + Wave 16 W16-γ / #9: ShopEquipment は apply 内で Gold 消費のみ。Gold が減っていれば
    // 購入成功 = ロードアウトに永続装着 + ラン中の player.equipment + drawPile にも即時反映 (CTO #4)。
    if (choice instanceof LayerEndNode.ShopEquipment se
        && before.gold().amount() != upgraded.gold().amount()) {
      Equipment eq = equipmentCatalog().get(se.equipmentId());
      equipInLoadout(eq); // 永続追加 (既存、ラン後に持ち越し)

      // CTO #4: ラン中 player の装備マップ + デッキにも即時反映 (詐欺 UI 解消)。
      java.util.Map<EquipmentSlot, Equipment> newEquipMap =
          new java.util.HashMap<>(upgraded.statuses().equipment());
      newEquipMap.put(eq.slot(), eq);
      java.util.List<Card> newDrawCards =
          new java.util.ArrayList<>(upgraded.cardPileState().drawPile().cards());
      for (CardId cid : eq.grantedCards()) {
        newDrawCards.add(InitialStateFactory.resolveCard(cid));
      }
      // Random でシャッフル: 山札末尾固定だと次ターン頭に偏って引かれるため自然分散
      java.util.Collections.shuffle(newDrawCards, session.rng());
      Player beforeMaxHpSync =
          upgraded
              .withStatuses(upgraded.statuses().withEquipment(newEquipMap))
              .withCardPileState(
                  new CardPileState(
                      new DrawPile(newDrawCards),
                      upgraded.cardPileState().hand(),
                      upgraded.cardPileState().discardPile()));

      // CTO #3: 装備で maxHp が増加した場合、currentHp も同じ差分だけ加算 (「最大 HP だけ伸びて
      // 現在 HP の割合が減る = ショップで買ったのに傷ついて見える」UX 違和感を構造的に解消)。
      int maxHpDelta = beforeMaxHpSync.effectiveStats().maxHp() - before.effectiveStats().maxHp();
      if (maxHpDelta > 0) {
        upgraded = beforeMaxHpSync.withStats(beforeMaxHpSync.stats().healed(maxHpDelta));
      } else {
        upgraded = beforeMaxHpSync;
      }
    }
    DungeonState withUpgrade = current.withPlayer(upgraded);
    // GameContext.maxLayer を渡し、SoulTree.LayerExtendEffect で拡張済の最終層番号を反映
    session
        .director()
        .advanceFloor(
            InitialStateFactory.advanceLayer(
                withUpgrade, session.rng(), session.context().maxLayer()));
    // Wave 10 W10-α: ステ強化ノード (HpMaxUp / SpeedUp) で STATUS_UP SE を発火
    if ((choice instanceof LayerEndNode.HpMaxUp || choice instanceof LayerEndNode.SpeedUp)
        && resources != null) {
      resources.soundManager().playSe(core.infrastructure.audio.SeKind.STATUS_UP);
    }
    recordObtainedCards(); // §15-3: ショップノードで入手したカードを図鑑に記録
    // §15-11: 層境界 (次層に進入する前) でセーブする。
    saveAtLayerBoundary();
  }

  /**
   * §15-3 / §15-6: 強化個体撃破時にカード追加効果を Player に適用する (層遷移は伴わない)。
   *
   * <p>{@link LayerEndNode#apply(Player, core.domain.layer.NodeResolveContext)} を呼ぶだけで、{@link
   * core.application.TurnDirector#advanceFloor} は呼ばない (戦闘継続中のため)。
   */
  public void applyEliteCardReward(LayerEndNode choice) {
    Objects.requireNonNull(choice, "choice");
    RunSession session = requireRunSession();
    DungeonState current = session.context().state();
    Player upgraded = choice.apply(current.player(), nodeResolveContext());
    session
        .context()
        .applyResult(new StepResult(current.withPlayer(upgraded), java.util.List.of()));
    recordObtainedCards(); // §15-3: 強化個体報酬で入手したカードを図鑑に記録
  }

  // =========================================================================
  // §15-11 セーブ / ロード / §15-1 設定
  // =========================================================================

  /** 永続化サービスの集約 (Wave 9 W9-β、SaveManager / SettingsManager / Settings)。 */
  public PersistenceServices persistence() {
    return persistence;
  }

  /**
   * 設定を更新してフルスクリーン + 音量を即時反映する (§15-1、Wave 9 W9-β CTO レビュー反映 #3)。
   *
   * <p>処理シーケンス:
   *
   * <ol>
   *   <li>{@link PersistenceServices#apply}: 永続データの更新 (純粋なデータ層)
   *   <li>{@link PersistenceServices#save}: ファイルへの即時保存
   *   <li>{@link #updateHardwareConfigurations}: グラフィック / サウンドの副作用 (LibGDX 依存、DddGame が仲介)
   * </ol>
   *
   * <p>PersistenceServices は LibGDX 非依存に保たれ、infrastructure 層の純粋性を維持する。
   */
  public void applySettings(Settings newSettings) {
    Objects.requireNonNull(newSettings, "newSettings");
    persistence.apply(newSettings); // 1. 永続データの更新
    persistence.save(); // 2. ファイルへの即時保存
    updateHardwareConfigurations(newSettings); // 3. グラフィック/サウンドの副作用
  }

  /**
   * グラフィック / サウンドへ Settings を即時反映する (Wave 9 W9-β、LibGDX 依存の副作用を DddGame に集約)。
   *
   * <p>本メソッドは {@link #applySettings} の内部呼出専用 = PersistenceServices に副作用を逆流させないための分離。
   */
  private void updateHardwareConfigurations(Settings newSettings) {
    // フルスクリーン切替を実機反映
    if (newSettings.fullscreen()) {
      com.badlogic.gdx.Graphics.DisplayMode mode = com.badlogic.gdx.Gdx.graphics.getDisplayMode();
      com.badlogic.gdx.Gdx.graphics.setFullscreenMode(mode);
    } else {
      // DesktopLauncher と同じ 1920×1080 に戻す (1280×720 ハードコードだと解像度が縮む)。
      com.badlogic.gdx.Gdx.graphics.setWindowedMode(1920, 1080);
    }
    // §15-5: サウンドマネージャに音量を即時反映する。
    if (resources != null && resources.soundManager() != null) {
      resources.soundManager().applySettings(newSettings);
    }
  }

  /**
   * 現在の設定をファイルに保存する (§15-1、初回プリセット選択後等のシンプルな保存パス用)。{@link #applySettings} は中で save を呼ぶため、
   * 通常は直接呼ばない。
   */
  public void saveSettings() {
    persistence.save();
  }

  /**
   * 層境界 (層末ノード解決直後) に呼ぶ。現在の live 状態を {@link SaveData} に変換して保存する。
   *
   * <p>context が null (ラン未開始) の場合は何もしない (ガード)。
   */
  public void saveAtLayerBoundary() {
    if (runSession.isEmpty()) {
      return;
    }
    DungeonState state = runSession.get().context().state();
    // ロード後に進入する層番号 = 現在の次層番号 (advanceFloor 済みなので state.layer() が次層)
    int nextLayerNumber = state.layer().number();

    // SAVE-03B: 旧 save.json ではなく Profile / RunCheckpoint へ書く。
    // 書込側だけ旧形式のままにすると、タイトルの「つづき」判定 (新形式) と食い違い、
    // 層境界で保存したはずの進捗が次回起動で無視される。
    RunLifecycle lifecycle = persistence.runLifecycle();
    int initialSoul = runSession.map(RunSession::initialRunSoul).orElse(0);
    RunCheckpoint checkpoint =
        RunCheckpointMapper.toCheckpoint(
            runSession.get().runId(),
            state.player(),
            nextLayerNumber,
            ProfileDataMapper.toRunInventory(progress),
            ProfileDataMapper.currentCapacity(progress),
            initialSoul);
    // 層境界では恒久ソウルを据え置く。ラン中は progress.playerSoul() が 0 (開始時に Player へ
    // 注入済) なので、そのまま書くと精算前に終了したプレイヤーの貯金が全損する。
    ProfileData previous = lifecycle.profileOrInitial();
    ProfileData profile = ProfileDataMapper.forLayerBoundary(progress, previous);
    if (!lifecycle.saveAtLayerBoundary(profile, checkpoint).isSuccess()) {
      LOG.severe("層境界セーブに失敗しました。次の層境界で再試行します。");
      if (getScreen() instanceof DungeonScreen dungeonScreen) {
        boolean jp =
            resources == null
                || resources.fonts() == null
                || resources.fonts().isJapaneseAvailable();
        dungeonScreen.showFlash(
            jp ? Strings.Ja.LAYER_SAVE_FAILED_FLASH : Strings.En.LAYER_SAVE_FAILED_FLASH);
      }
    }
  }

  /**
   * セーブデータからランを復元する (§15-11)。
   *
   * <p>ロード後は InitialStateFactory で指定層番号のマップを新規生成し、プレイヤーのステ / デッキ / メタ進捗を復元する。 セーブデータが存在しない /
   * 破損している場合は何もしない。
   *
   * @return ロードに成功した場合 true
   */
  public boolean loadFromSave() {
    // SAVE-03B: 新形式 (Profile + RunCheckpoint) から復元する。旧 save.json はもう書かれないため、
    // ここが旧形式を読んだままだと「つづき」を押しても何も起きない。
    RunLifecycle lifecycle = persistence.runLifecycle();
    Optional<RunCheckpoint> optCheckpoint = lifecycle.resumableCheckpoint();
    if (optCheckpoint.isEmpty()) {
      return false;
    }
    RunCheckpoint checkpoint = optCheckpoint.get();
    SaveData data =
        RunCheckpointMapper.toRestorableSaveData(lifecycle.profileOrInitial(), checkpoint);

    // メタ進捗を一括復元 (Wave 6 W6-γ: PlayerProgress 1 record で集約)
    // playerSoul は Player 側が Checkpoint の総量を持つため zero にする (下記の二重計上防止を参照)。
    //
    // 所有装備は Profile を正とする。7 引数コンストラクタは loadout から所有を再生成するため、
    // ここで使うと「所有しているが装着していない装備」が再開のたびに消え、次の保存で永久に失われる。
    Map<EquipmentSlot, Equipment> restoredLoadout = SaveDataConverter.toLoadout(data);
    SoulTree restoredTree = SaveDataConverter.toSoulTree(data);
    progress =
        new PlayerProgress(
            Soul.zero(),
            data.runCount(),
            data.tutorialSeen(),
            SaveDataConverter.toObtainedCards(data),
            SaveDataConverter.toBestiary(data),
            restoredLoadout,
            restoredTree,
            ProfileDataMapper.mergeOwnershipForResume(
                lifecycle.profileOrInitial(), restoredLoadout, restoredTree));

    // ラン状態を復元: 指定層からの新規マップ生成 + プレイヤーステ / デッキ注入
    Random newRng = new Random();
    // §15-6 / SoulTree LayerExtend: ロード再開でも最大層数を再計算 (SaveData の SoulTree から派生)。
    int extendAmount = totalLayerExtendAmount();
    int maxLayer = InitialStateFactory.DEFAULT_MAX_LAYER + extendAmount;
    DungeonState baseState =
        InitialStateFactory.restoreLayer(data, progress.loadout(), newRng, maxLayer);
    // §15-7 CRITICAL FIX: ロード時にソウルツリー効果を再適用しない (SaveData は補正済 Stats/Deck/SkillSlot を
    // 保存しているため、再適用すると HP / カード / スキル枠が二重加算される)。
    Player withTree = baseState.player();
    // SAVE-03B: ここで soulTotal を加算しない。restoreLayer が既に Checkpoint の
    // currentRunSoul (= 持越し + ラン中獲得の総量) を Player へ入れているため、
    // profile.soulTotal を足すと持越し分が二重計上される。
    //
    // 旧実装が加算していたのは、層境界セーブが profile.soulTotal を 0 で潰していて
    // addSoul(0) が無害だったからである。層境界で恒久ソウルを据え置くようにした結果、
    // この加算が「再開のたびにソウルが増える」バグへ変わった。
    Player withSoul = withTree;
    // playerSoul はもう Soul.zero() (前段の new PlayerProgress で初期化済)、注入後の保持は維持

    DungeonState restoredState = baseState.withPlayer(withSoul);
    GameContext newContext = GameContext.startNewRun(restoredState);
    if (extendAmount > 0) {
      newContext.extendMaxLayer(extendAmount);
    }
    TurnDirector newDirector = new TurnDirector(newContext, newRng);
    // SAVE-03B: Checkpoint 由来の runId を引き継ぐ。新規発番すると Profile.activeRunId と
    // 食い違い、再開したランを以降保存できなくなる。
    runSession =
        Optional.of(
            new RunSession(
                RunCheckpointMapper.toRunId(checkpoint),
                newContext,
                newDirector,
                newRng,
                checkpoint.initialRunSoul()));
    return true;
  }

  /**
   * 画面を切り替え、旧 Screen を dispose する。
   *
   * <p>LibGDX の {@link Game#setScreen} は旧 Screen を dispose しないためリークする。本ヘルパー経由で 全ての画面遷移を行うことで、旧
   * Screen のリソース解放を保証する。
   */
  public void changeScreen(Screen next) {
    Screen old = getScreen();
    setScreen(next);
    if (old != null) {
      old.dispose();
    }
  }

  @Override
  public void create() {
    // §設計原則 / Wave 5 W5-γ: SoulTree のノードマスタ Supplier を最初に注入
    // (domain → infrastructure 依存方向違反を解消、Logger 級の単発 init setter)。
    SoulTree.setNodeProvider(core.infrastructure.bootstrap.InitialStateFactory::soulTreeNodes);
    // Wave 9 W9-β: 永続化サービスを一括初期化 (SaveManager + SettingsManager + Settings、resources より前)
    this.persistence = PersistenceServices.load();
    // SAVE-03B: 恒久進捗を profile.json から復元する。これを飛ばすと progress が空のまま
    // 走り出し、次のラン開始で beginRun が空の値で profile.json を全上書きして
    // ソウル・ツリー解放・図鑑・周回数を消す (書込系は previous からラン ID しか
    // 引き継がず、他はすべて progress から再構築するため)。
    this.progress =
        ProfileDataMapper.toPlayerProgress(
            persistence.runLifecycle().profileOrInitial(), defaultLoadout());
    // Wave 8 W8-β: LibGDX リソース 3 件 (Fonts / SoundManager / CardImageRegistry) を GameResources
    // で一括初期化
    this.resources =
        GameResources.load(
            persistence.settings(),
            core.infrastructure.bootstrap.InitialStateFactory.cardCatalog(),
            core.infrastructure.bootstrap.InitialStateFactory.equipmentCatalog());
    // §15-7 / E-2: startNewRun() はラン開始の瞬間 (TitleScreen の ENTER) でのみ呼ぶ。
    // ここで呼ぶと獲得前の playerSoul が Player に注入・ゼロ化され、ソウルツリーで使えなく
    // なる (ソウル消失バグの根治)。RunSession は最初のラン開始まで Optional.empty()。
    // §15-1: 初回起動 (settings.json 未存在) は UI プリセット選択画面を最初に表示する。
    if (!persistence.settingsManager().exists()) {
      changeScreen(new FirstRunPresetScreen(this));
    } else {
      changeScreen(new TitleScreen(this));
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    // Wave 8 W8-β: GameResources で防衛的 dispose (try-catch + null check 内包)
    if (resources != null) {
      resources.dispose();
    }
  }
}
