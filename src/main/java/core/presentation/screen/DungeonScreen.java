package core.presentation.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import core.application.TurnDirector;
import core.domain.battle.BattleAction;
import core.domain.battle.BattleEvent;
import core.domain.battle.TurnPhase;
import core.domain.card.Card;
import core.domain.card.CardRarity;
import core.domain.card.Hand;
import core.domain.common.Direction;
import core.domain.dungeon.DungeonState;
import core.domain.entity.ActorId;
import core.domain.entity.Enemy;
import core.domain.layer.LayerEndNode;
import core.infrastructure.audio.BgmKind;
import core.infrastructure.audio.SeKind;
import core.presentation.input.PlayerInputs;
import core.presentation.render.DungeonRenderer;
import core.presentation.render.HudRenderer;
import core.presentation.render.RenderLayout;
import core.presentation.render.Strings;
import core.presentation.render.UiTheme;
import core.presentation.window.NodeChoicePopup;
import core.presentation.window.StatusPopup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * ゲーム本編画面。
 *
 * <p>毎フレーム: 入力受付 → TurnEngine 解決 → 描画 → フェーズ遷移チェック の流れで動く。 描画と入力以外のロジックは {@link TurnDirector}
 * に委譲する。
 *
 * <p>{@code TurnDirector} と {@code Fonts} は {@link DddGame} 側で保持されている個体を都度参照する
 * (新ラン後に古い参照を見続ける事故を構造的に防ぐ)。
 *
 * <p>{@link PlayerInputs} はインスタンスとして保持し、2 ステートモデル (通常 / カード選択中) のカード選択状態を フレーム間で維持する。{@link
 * #show()} / {@link #hide()} / {@link #dispose()} でリセットする。
 *
 * <p>{@link NodeChoicePopup} は CLEARED 状態 (階段踏破直後) に lazy 初期化され、 1〜3 数字キーで選択された後 {@link
 * DddGame#resolveLayerEndChoice} → 自動的に次層へ遷移し、ポップアップは dispose される (§15-8 / E-6)。
 */
public final class DungeonScreen extends ScreenAdapter {

  /**
   * 敵ターンの総表示時間を約 1.2 秒以下に収める可変ステップ間隔を算出する (Task 2 / 案 A)。
   *
   * @param totalAp 敵全体の現在 AP 合計
   * @return ステップ間隔 (秒、0.03s <= interval <= 0.10s)
   */
  public static float calculateEnemyStepInterval(int totalAp) {
    if (totalAp <= 0) {
      return 0.05f;
    }
    float targetInterval = 1.2f / totalAp;
    return Math.max(0.03f, Math.min(0.10f, targetInterval));
  }

  private final DddGame game;

  /** 乱数源 (ADR-19: 引数注入)。カード抽選 / 層末ノード抽選 / Elite 報酬シャッフルに使う。 */
  private final Random rng;

  /** Wave 4 W4-β: 副作用エフェクト (popup / shake / flash / lowHpAnim) を集約。 */
  private final ScreenEffects effects = new ScreenEffects();

  /** GameContext.totalEventsEmitted の前回観測値。新規 DamageDealt 検知のカーソル。 */
  private long lastSeenEventCount = 0;

  /** 敵ターンの 1 アクション刻みタイマー (§15-5、ENEMY_STEP_INTERVAL ごとに 1 アクション進める)。 */
  private float enemyStepTimer = 0f;

  /**
   * §15-5 / E-7: 撃破時に Bestiary へ EnemyKind を記録するためのメモリ。 Wave 4 W4-α で純粋データクラスに切り出し
   * (テスト容易性向上、SaveData 永続化への布石)。
   */
  private final EnemyKindMemory enemyKindMemory = new EnemyKindMemory();

  /** マップ描画用カメラ (プレイヤー追従)。 */
  private OrthographicCamera camera;

  /** HUD 描画用カメラ (画面固定、追従カメラに引きずられない)。 */
  private OrthographicCamera hudCamera;

  private Viewport viewport;
  private SpriteBatch batch;
  private ShapeRenderer shapes;
  private PlayerInputs playerInputs;
  private NodeChoicePopup nodeChoice;

  /** §15-3 / §15-6: 強化個体撃破報酬 UI ライフサイクル管理 (Wave 4 W4-γ で DungeonScreen から切り出し)。 */
  private EliteRewardOrchestrator eliteReward;

  /** §15-4: ステータス確認ポップアップ (Tab で開閉)。show() で 1 度だけ生成。 */
  private StatusPopup statusPopup;

  /** ステータスポップアップ表示中フラグ。true の間はプレイヤー入力・進行を凍結する (モーダル)。 */
  private boolean statusPanelOpen;

  /** ダンジョン内の ESC 一時停止メニュー。 */
  private PauseMenuController pauseMenu;

  /** マップタイル: 壁テクスチャ (チームメイト素材、ピクセルアート、Nearest filter)。 */
  private com.badlogic.gdx.graphics.Texture wallTexture;

  /** マップタイル: 床テクスチャ (チームメイト素材、ピクセルアート、Nearest filter)。 */
  private com.badlogic.gdx.graphics.Texture floorTexture;

  /**
   * HP_LOW SE の連続発火防止フラグ (Wave 10 W10-β-2)。HP > 30% → ≤ 30% 遷移時のみ発火、 30%
   * 超に回復した瞬間にリセット。毎フレーム鳴り続けるのを防ぐ。
   */
  private boolean lowHpSeFired = false;

  /** プレイヤースプライト (Wave 10 W10-β、チームメイト素材)。 */
  private com.badlogic.gdx.graphics.Texture playerTexture;

  /** 階段タイル (2026-05-24 素材投入、旧 ShapeRenderer 黄色マーカーを置換)。 */
  private com.badlogic.gdx.graphics.Texture stairsTexture;

  /** 罠マーカー (2026-05-24 素材投入、物理/魔法は SpriteBatch.setColor でティント)。 */
  private com.badlogic.gdx.graphics.Texture trapTexture;

  /** 敵スプライト群 (Wave 10 W10-β、EnemyKind → Texture、ELITE_SLIME は SLIME を流用)。 */
  private java.util.Map<core.domain.entity.EnemyKind, com.badlogic.gdx.graphics.Texture>
      enemyTextures;

  public DungeonScreen(DddGame game) {
    this.game = game;
    // Wave 9 W9-α: rng は RunSession 経由で取得 (ラン未開始時は IllegalStateException)
    this.rng = game.requireRunSession().rng();
  }

  @Override
  public void show() {
    camera = new OrthographicCamera();
    // §15-6 UI/UX: マップを 2 倍拡大表示 (zoom < 1 が拡大方向)。視界 12x6 マス、プレイヤー常に画面中央。
    camera.zoom = 0.5f;
    viewport = new FitViewport(RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT, camera);
    hudCamera = new OrthographicCamera();
    hudCamera.setToOrtho(false, RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT);
    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    playerInputs = new PlayerInputs();
    // Wave 15 W15-β / #3: SkillSlot 完全廃止 (デッキ・カード一本化、§15-3 整合)。
    // bindSkillSlotSizeSupplier は不要 (F1-F4 入力経路を PlayerInputs から削除済)。
    statusPopup = new StatusPopup(game.fonts().large(), game.fonts().isJapaneseAvailable());
    eliteReward = new EliteRewardOrchestrator(game, rng, effects);
    pauseMenu = new PauseMenuController(game);
    // マップタイルテクスチャをロード (ピクセルアート → Nearest filter で 80px 拡大時のボケ防止)。
    wallTexture = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("tiles/wall.png"));
    wallTexture.setFilter(
        com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
        com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);
    floorTexture = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("tiles/floor.png"));
    floorTexture.setFilter(
        com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
        com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);
    stairsTexture = loadPixelTexture("tiles/stairs.png");
    trapTexture = loadPixelTexture("tiles/trap.png");
    // Wave 10 W10-β: キャラスプライトをロード (player + 4 種の敵 Texture、Nearest filter)
    playerTexture = loadPixelTexture("sprites/player.png");
    com.badlogic.gdx.graphics.Texture slimeTex = loadPixelTexture("sprites/slime.png");
    enemyTextures = new java.util.EnumMap<>(core.domain.entity.EnemyKind.class);
    enemyTextures.put(core.domain.entity.EnemyKind.SLIME, slimeTex);
    // Wave 10 W10-β-2: 速い = スケルトン、頑強 = ゴブリンに割当 (ユーザー意図に合わせて入れ替え)
    enemyTextures.put(
        core.domain.entity.EnemyKind.SWIFT_SLIME, loadPixelTexture("sprites/skeleton.png"));
    enemyTextures.put(
        core.domain.entity.EnemyKind.TOUGH_SLIME, loadPixelTexture("sprites/goblin.png"));
    // ELITE_SLIME は SLIME と同じ Texture を流用、DungeonRenderer 側で SpriteBatch.setColor の赤ティント
    enemyTextures.put(core.domain.entity.EnemyKind.ELITE_SLIME, slimeTex);
    enemyTextures.put(
        core.domain.entity.EnemyKind.BOSS, loadPixelTexture("sprites/dragon_boss.png"));
    // §15-5: ダンジョン BGM を開始 (既に再生中なら no-op)
    game.soundManager().playBgm(BgmKind.DUNGEON);
  }

  /** ピクセルアート Texture を Nearest filter でロードするヘルパ (Wave 10 W10-β)。 */
  private static com.badlogic.gdx.graphics.Texture loadPixelTexture(String internalPath) {
    com.badlogic.gdx.graphics.Texture tex =
        new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(internalPath));
    tex.setFilter(
        com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
        com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest);
    return tex;
  }

  @Override
  public void render(float delta) {
    // すでに一時停止メニューが表示中のフレーム: ゲーム側の進行・エフェクト・ポップアップ入力はすべて停止する。
    // メニューを閉じたフレームも、同じキーやクリックがゲーム操作に流れないよう描画して即 return する。
    if (pauseMenu != null && pauseMenu.isActive()) {
      if (pauseMenu.handleInput()) {
        return; // 画面遷移済み: dispose された DungeonScreen の batch やダイアログに触れない
      }
      drawFrame();
      if (pauseMenu.isActive()) {
        pauseMenu.render(delta);
      }
      return;
    }

    // ESC の優先順位 (フレーム先頭の状態で先勝ち分岐し、1 回の ESC で 1 動作だけ行う):
    // 1. ステータスポップアップ表示中 → 閉じる (handleStatusPanelToggle)
    // 2. プレイヤーターン中にカード選択中 (報酬・層末ポップアップ非表示) → 選択キャンセル (PlayerInputs)
    // 3. それ以外 (プレイヤーターン / 敵ターン / 層末ノード選択中 / Elite 報酬表示中) → 一時停止メニューを開く
    boolean wasStatusPanelOpen = statusPanelOpen;
    boolean canCancelCardSelection =
        !wasStatusPanelOpen
            && nodeChoice == null
            && !eliteReward.isActive()
            && game.requireRunSession().context().state().phase() == TurnPhase.PLAYER_TURN
            && playerInputs.pendingCardIndex() >= 0;

    if (!wasStatusPanelOpen
        && !canCancelCardSelection
        && pauseMenu != null
        && Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      pauseMenu.open();
      drawFrame();
      pauseMenu.render(delta);
      return;
    }

    handleStatusPanelToggle(); // §15-4: Tab / ESC でステータスポップアップ開閉
    if (!statusPanelOpen && !wasStatusPanelOpen) {
      // ステータスポップアップ・Elite 報酬ポップアップ表示中はゲーム進行を凍結する (モーダル)。
      // またステータスを閉じた同一フレームでもゲーム入力を走らせない (ESC 二重処理防止)。
      if (!eliteReward.isActive()) {
        updateState(delta);
      }
      processNewEvents(); // §15-5 / E-8: 新規 DamageDealt → popup + shake / EliteDefeated → カード追加 UI
      effects.advanceEffects(delta); // popup の age 加算 + 期限切れ除去 + shake デクリメント + flash デクリメント
      if (eliteReward.handleInput()) { // §15-3 / §15-6: Elite 撃破 popup の入力処理
        playerInputs.reset();
      }
    }
    drawFrame();
    // Popup は HUD の上に重ねて描画する (CLEARED 中の前面 UI)。drawFrame() で batch.end() 済みのため、
    // Stage の SpriteBatch とは衝突しない (描画スタックの順序: ダンジョン → HUD → Popup)。
    if (nodeChoice != null) {
      nodeChoice.render(delta);
    }
    eliteReward.render(delta);
    if (statusPanelOpen) {
      statusPopup.updateValues(game.requireRunSession().context().state().player());
      statusPopup.render(delta);
    }
    transitionIfGameOver();
  }

  /**
   * §15-4: Tab キーでステータスポップアップを開閉する。
   *
   * <p>開ける条件は「プレイヤーターン中」かつ「他ポップアップ (層末ノード / Elite カード選択) 非表示」。 開いている間は Tab / ESC のどちらでも閉じられる。
   */
  private void handleStatusPanelToggle() {
    if (statusPanelOpen) {
      if (Gdx.input.isKeyJustPressed(Keys.TAB) || Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
        statusPanelOpen = false;
      }
      return;
    }
    boolean otherPopupActive = nodeChoice != null || eliteReward.isActive();
    if (!otherPopupActive
        && game.requireRunSession().context().state().phase() == TurnPhase.PLAYER_TURN
        && Gdx.input.isKeyJustPressed(Keys.TAB)) {
      statusPanelOpen = true;
    }
  }

  /** 敵ターンの進入時行動予算 (Phase 2-2: 可変表示時間 1.2s 計算の基準値)。 */
  private int initialEnemyActionBudget = -1;

  private void updateState(float delta) {
    TurnDirector director = game.requireRunSession().director();
    TurnPhase phase = game.requireRunSession().context().state().phase();
    if (phase == TurnPhase.PLAYER_TURN) {
      enemyStepTimer = 0f;
      initialEnemyActionBudget = -1;
      // §15-3 UI 改善: マウスクリックでカード選択 (キーボード入力に先んじて処理)
      boolean cardConsumed = handleHandMouseClick();
      // Wave 14 W14-β: ターン終了ボタンクリック判定 (画面右下、カード未消費 + ポップアップ非表示時のみ)
      boolean endTurnConsumed = !cardConsumed && handleEndTurnButtonClick(director);
      // Wave 14 W14-α: マップタイルクリックで方向選択 → Move / UseCard (カード/ボタン未消費 +
      // ポップアップ非表示時のみ反応、二重発火回避)
      Optional<Direction> mouseDir =
          (cardConsumed
                  || endTurnConsumed
                  || nodeChoice != null
                  || eliteReward.isActive()
                  || statusPanelOpen)
              ? Optional.empty()
              : PlayerInputs.readMouseDirection(
                  viewport, game.requireRunSession().context().state().player().position());
      Optional<BattleAction> action =
          playerInputs.poll(game.requireRunSession().context().state(), mouseDir);
      action.ifPresent(director::applyPlayerAction);
    } else if (phase == TurnPhase.ENEMY_TURN) {
      // Phase 2-2: 敵ターンの表示時間を約 1.2s 以下に収める可変ステップ間隔
      if (initialEnemyActionBudget < 0) {
        int totalAp = 0;
        for (core.domain.entity.Enemy enemy :
            game.requireRunSession().context().state().enemies()) {
          totalAp += enemy.actionPoints().current();
        }
        initialEnemyActionBudget = Math.max(1, totalAp);
      }
      float stepInterval = calculateEnemyStepInterval(initialEnemyActionBudget);
      enemyStepTimer += delta;

      int maxStepsPerFrame = 10;
      int stepCount = 0;
      while (enemyStepTimer >= stepInterval
          && game.requireRunSession().context().state().phase() == TurnPhase.ENEMY_TURN
          && stepCount < maxStepsPerFrame) {
        enemyStepTimer -= stepInterval;
        stepCount++;
        director.stepEnemyTurnOnce();

        // 敵全員の AP が尽きた場合、フェーズ完了 (PLAYER_TURN 復帰) のためにタイマー待ちせず直ちにステップを完了させる
        if (game.requireRunSession().context().state().phase() == TurnPhase.ENEMY_TURN) {
          int remainingAp = 0;
          for (core.domain.entity.Enemy enemy :
              game.requireRunSession().context().state().enemies()) {
            remainingAp += enemy.actionPoints().current();
          }
          if (remainingAp <= 0) {
            director.stepEnemyTurnOnce();
          }
        }
      }
    } else if (phase == TurnPhase.CLEARED) {
      // 階段踏破直後の層末ノード選択フロー (§15-8 / E-6)。
      // この間 ENEMY_TURN への遷移は起きず、敵は静止する (CLEARED は層遷移の前段で全行動凍結)。
      handleLayerEndChoice();
    }
    // RUN_CLEARED / GAME_OVER は transitionIfGameOver() が画面遷移を担う。
  }

  /**
   * CLEARED 状態の入力ハンドリング:
   *
   * <ol>
   *   <li>ポップアップが未生成なら固定 3 提示で lazy 初期化
   *   <li>数字キー 1/2/3 で {@link NodeChoicePopup#select(int)}
   *   <li>選択結果があれば {@link DddGame#resolveLayerEndChoice} を呼び、ポップアップを dispose
   * </ol>
   */
  private void handleLayerEndChoice() {
    if (nodeChoice == null) {
      nodeChoice = createNodeChoicePopup();
    }
    for (int i = 0; i < NodeChoicePopup.CHOICE_COUNT; i++) {
      if (Gdx.input.isKeyJustPressed(numKey(i))) {
        nodeChoice.select(i);
      }
    }
    nodeChoice
        .consume()
        .ifPresent(
            choice -> {
              // 確定前に支払い可否を判定し、不可なら理由を flash 表示して同じ 3 択を維持する (F16)。
              core.domain.entity.Player before =
                  game.requireRunSession().context().state().player();
              boolean jp = game.fonts().isJapaneseAvailable();
              if (!choice.canAfford(before)) {
                if (choice instanceof LayerEndNode.Shop shop) {
                  showFlash(
                      (jp
                              ? Strings.Ja.SHOP_INSUFFICIENT_GOLD_FORMAT
                              : Strings.En.SHOP_INSUFFICIENT_GOLD_FORMAT)
                          .formatted(shop.goldCost()));
                } else if (choice instanceof LayerEndNode.ShopEquipment se) {
                  showFlash(
                      (jp
                              ? Strings.Ja.SHOP_INSUFFICIENT_GOLD_FORMAT
                              : Strings.En.SHOP_INSUFFICIENT_GOLD_FORMAT)
                          .formatted(se.goldCost()));
                } else if (choice instanceof LayerEndNode.Event ev) {
                  if (ev.soulDelta() < 0 && before.soul().amount() < -ev.soulDelta()) {
                    showFlash(
                        (jp
                                ? Strings.Ja.EVENT_INSUFFICIENT_SOUL_FORMAT
                                : Strings.En.EVENT_INSUFFICIENT_SOUL_FORMAT)
                            .formatted(-ev.soulDelta()));
                  } else if (ev.goldDelta() < 0 && before.gold().amount() < -ev.goldDelta()) {
                    showFlash(
                        (jp
                                ? Strings.Ja.SHOP_INSUFFICIENT_GOLD_FORMAT
                                : Strings.En.SHOP_INSUFFICIENT_GOLD_FORMAT)
                            .formatted(-ev.goldDelta()));
                  }
                }
                boolean hasAffordableChoice = false;
                for (LayerEndNode candidate : nodeChoice.choices()) {
                  if (candidate.canAfford(before)) {
                    hasAffordableChoice = true;
                    break;
                  }
                }
                if (hasAffordableChoice) {
                  return;
                }
                showFlash(
                    jp
                        ? Strings.Ja.LAYER_END_NO_AFFORDABLE_FLASH
                        : Strings.En.LAYER_END_NO_AFFORDABLE_FLASH);
              }
              game.resolveLayerEndChoice(choice);
              nodeChoice.dispose();
              nodeChoice = null;
            });
  }

  /** カードマスタ (cards.json) からランダムに 1 枚返す (層末ショップノードのカード抽選用)。 */
  private core.domain.card.Card randomCatalogCard() {
    List<core.domain.card.Card> all = new ArrayList<>(game.cardCatalog().all());
    Collections.shuffle(all, rng);
    return all.get(0);
  }

  /**
   * 装備マスタ (equipment.json) からランダムに 1 件の EquipmentId を返す (Wave 3 Task B、 装備購入ノードの抽選用)。空カタログ時は IAE
   * (起動時に EquipmentCatalog が空ならハードコードで詰む設計)。
   */
  private core.domain.equipment.EquipmentId randomEquipmentId() {
    List<core.domain.equipment.Equipment> all = new ArrayList<>(game.equipmentCatalog().all());
    if (all.isEmpty()) {
      throw new IllegalStateException("equipment catalog is empty; cannot draw ShopEquipment");
    }
    return all.get(rng.nextInt(all.size())).id();
  }

  /** フラッシュメッセージを表示する (effects への委譲)。 */
  void showFlash(String message) {
    effects.showFlash(message);
  }

  /**
   * 抽選 5 候補から 3 提示の {@link NodeChoicePopup} を生成する (§15-8 完成仕様縮退)。
   *
   * <p>§15-8 仕様の「4 種カテゴリ (ステ強化 / 休憩 / イベント / ショップ) から 3 提示」を、本セッション では 5 候補 (HpMaxUp / SpeedUp /
   * Rest / Shop / Event) から Random で 3 選に縮退。 候補プールに新 LayerEndNode を増やしたい場合は本メソッドの allCandidates
   * に追加するだけ。
   */
  private NodeChoicePopup createNodeChoicePopup() {
    List<LayerEndNode> allCandidates =
        new ArrayList<>(
            List.of(
                new LayerEndNode.HpMaxUp(5),
                new LayerEndNode.SpeedUp(1),
                new LayerEndNode.Rest(),
                // Wave 3 Task A: Shop は CardId 保持。ランダム抽選カードを id だけ渡す。
                new LayerEndNode.Shop(5, randomCatalogCard().id()),
                // Wave 8 W8-α: Event は EventKind 経由で構築 (旧 displayLabel: String 撤去、ドメイン日本語汚染解消)
                LayerEndNode.Event.of(core.domain.layer.EventKind.SOUL_SHRINE),
                LayerEndNode.Event.of(core.domain.layer.EventKind.HEALING_SPRING),
                LayerEndNode.Event.of(core.domain.layer.EventKind.GOLDEN_CHEST),
                // Wave 3 Task B: 装備購入ノード (層末 6 候補 → 8 候補、3 提示は変えない)。
                new LayerEndNode.ShopEquipment(15, randomEquipmentId())));
    Collections.shuffle(allCandidates, rng);
    List<LayerEndNode> choices =
        List.copyOf(allCandidates.subList(0, NodeChoicePopup.CHOICE_COUNT));

    String title =
        game.fonts().isJapaneseAvailable()
            ? Strings.Ja.LAYER_END_TITLE
            : Strings.En.LAYER_END_TITLE;
    // large(32px) 等倍。hud(16px)+setFontScale(2f) は Scene2D で漢字が黒四角化するため使えない。
    return new NodeChoicePopup(
        game.fonts().large(),
        title,
        choices,
        game.nodeResolveContext(),
        game.fonts().isJapaneseAvailable());
  }

  /** 0-indexed の i (0〜2) を NUM_1〜NUM_3 にマップ。範囲外は IAE。 */
  private static int numKey(int i) {
    return switch (i) {
      case 0 -> Keys.NUM_1;
      case 1 -> Keys.NUM_2;
      case 2 -> Keys.NUM_3;
      default -> throw new IllegalArgumentException("i must be 0..2: " + i);
    };
  }

  private void drawFrame() {
    updateMapCamera(); // §15-6: プレイヤー追従 + 画面シェイク + マップ端クランプ

    // §7-2 / W4-ε: 現在の装備ロードアウトから UI テーマを取得 (毎フレーム解決、コスト無視できる程度)。
    UiTheme theme = game.activeUiTheme();

    ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1f);
    viewport.apply();

    // マップ層: 追従カメラで描画 (タイルテクスチャ → 境界線 → 罠/敵/プレイヤー → ダメージポップアップ)。
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    DungeonRenderer.draw(
        batch,
        shapes,
        game.requireRunSession().context().state(),
        wallTexture,
        floorTexture,
        stairsTexture,
        trapTexture,
        playerTexture,
        enemyTextures);
    batch.begin();
    effects.drawPopups(batch, game.fonts().large());
    batch.end();

    // HUD 層: 固定カメラで描画。マップが画面全体を覆うため、HUD テキストの背後に半透明パネルを
    // 敷いて可読性を確保する (右上 = ステータス群、下部 = 手札 / ログ / 操作ヒント)。
    Gdx.gl.glEnable(GL20.GL_BLEND);
    shapes.setProjectionMatrix(hudCamera.combined);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0f, 0f, 0f, 0.55f);
    shapes.rect(1330f, 770f, 590f, 310f);
    // §15-3 UI 改善: 手札パネル背景を完全黒 (α=1) にしてマップタイルが透けないようにする
    shapes.setColor(0f, 0f, 0f, 1f);
    shapes.rect(0f, 0f, RenderLayout.SCREEN_WIDTH, 300f);
    // §7-2 / W4-ε: HP <= 30% で画面端を脈動するフレームで警告表示 (色はテーマ依存)。
    core.domain.entity.Stats playerStats =
        game.requireRunSession().context().state().player().stats();
    effects.drawLowHpWarning(shapes, playerStats, theme);
    shapes.end();

    batch.setProjectionMatrix(hudCamera.combined);
    batch.begin();
    HudRenderer.draw(
        batch,
        game.fonts(),
        game.requireRunSession().context(),
        playerInputs.pendingCardIndex(),
        game.cardImageRegistry(),
        game.persistence().settings().uiPreset(),
        theme);
    effects.drawFlash(batch, game.fonts().large(), theme);
    batch.end();
  }

  /**
   * 新規 {@link BattleEvent.DamageDealt} を検知してダメージポップアップを spawn + 画面シェイクをトリガする (§15-5 / E-8)。{@link
   * core.application.GameContext#totalEventsEmitted()} を前回観測値と比較し、 差分ぶんだけ {@code latestEvents}
   * で取得する。
   */
  private void processNewEvents() {
    // §15-5 / E-7: 生存中の敵 ID → kind を更新 (ActorDied 時の Bestiary 記録に使う、kind は ActorDied 自体に無い)。
    for (Enemy en : game.requireRunSession().context().state().enemies()) {
      enemyKindMemory.recordEnemy(en.id(), en.kind());
    }
    long current = game.requireRunSession().context().totalEventsEmitted();
    if (current <= lastSeenEventCount) {
      return;
    }
    int delta = (int) Math.min(current - lastSeenEventCount, 64L);
    List<BattleEvent> newEvents = game.requireRunSession().context().latestEvents(delta);
    ActorId playerId = game.requireRunSession().context().state().player().id();
    for (BattleEvent e : newEvents) {
      if (e instanceof BattleEvent.DamageDealt d) {
        effects.spawnPopup(d, playerId, game.requireRunSession().context().state());
        // §15-5: 与ダメ / 被ダメ SE
        if (d.to().equals(playerId)) {
          game.soundManager().playSe(SeKind.PLAYER_DAMAGED);
        } else {
          game.soundManager().playSe(SeKind.DEAL_DAMAGE);
        }
      } else if (e instanceof BattleEvent.ActorDied died && !died.who().equals(playerId)) {
        // §15-5 / E-7: 撃破済 EnemyKind を Bestiary に記録 (enemyKindMemory は processNewEvents
        // 冒頭で更新済、Wave 4 W4-α で純粋データクラスに分離)
        enemyKindMemory.getEnemyKind(died.who()).ifPresent(game::recordEnemyDefeated);
        // §15-5: 敵撃破 SE (プレイヤー死亡は除外)
        game.soundManager().playSe(SeKind.ENEMY_DEFEATED);
      } else if (e instanceof BattleEvent.SkillUsed) {
        // §15-5: カード使用 SE。Wave 10 W10-α: 種別別音分け (CARD_MAGIC / CARD_BUFF / CARD_MOVE
        // / CARD_TRAP) は BattleEvent.SkillUsed に CardEffect 情報を載せる仕様変更が必要なため
        // Wave 11+ 送り、現状は CARD_PHYSICAL を単一発火 (旧 CARD_USED 相当)。
        game.soundManager().playSe(SeKind.CARD_PHYSICAL);
      } else if (e instanceof BattleEvent.FloorAdvanced) {
        // §15-5: 層遷移 SE
        game.soundManager().playSe(SeKind.FLOOR_ADVANCE);
      } else if (e instanceof BattleEvent.EliteDefeated) {
        // §15-3 / §15-6: 強化個体撃破時にカード追加 UI を発火 (二重生成は EliteRewardOrchestrator 内で防止)
        // Wave 10 W10-β-2: 達成感演出のため LEVEL_UP 素材を Elite 撃破に流用 (新規 SeKind 追加せず KISS)
        game.soundManager().playSe(SeKind.LEVEL_UP);
        playerInputs.reset();
        eliteReward.triggerOnEliteDefeat();
      } else if (e instanceof BattleEvent.TurnPhaseChanged tpc
          && tpc.newPhase() == TurnPhase.PLAYER_TURN) {
        // Wave 11 W11-β: プレイヤーターン開始時、手札に含まれる最高 rarity に応じて CARD_DRAW SE を分岐。
        // 未指定カードは CardRarity.COMMON 扱い (Card.rarityOrDefault())。
        Hand hand = game.requireRunSession().context().state().player().cardPileState().hand();
        game.soundManager().playSe(drawSeFor(hand));
      } else if (e instanceof BattleEvent.WallBroken) {
        // Wave 11 W11-α: 壊れる壁破壊時に block_brake SE を再生
        game.soundManager().playSe(SeKind.BLOCK_BREAK);
      } else if (e instanceof BattleEvent.Moved m && !m.who().equals(playerId)) {
        // §UI 改善: 敵が画面外で動いた時、flash で「どこかで敵が動いているようだ」を表示。
        // 可視範囲 = camera.position ± (SCREEN_WIDTH/HEIGHT * camera.zoom / 2) (LibGDX
        // OrthographicCamera 標準)。
        // zoom=0.5 なら半幅 480, 半高 270 (world unit)。HUD パネル分の Y オフセットも camera.position に含まれる。
        float worldX = m.to().x() * RenderLayout.TILE_SIZE + RenderLayout.TILE_SIZE / 2f;
        float worldY = m.to().y() * RenderLayout.TILE_SIZE + RenderLayout.TILE_SIZE / 2f;
        float halfW = RenderLayout.SCREEN_WIDTH * camera.zoom / 2f;
        float halfH = RenderLayout.SCREEN_HEIGHT * camera.zoom / 2f;
        boolean offScreen =
            Math.abs(worldX - camera.position.x) > halfW
                || Math.abs(worldY - camera.position.y) > halfH;
        if (offScreen) {
          boolean jp = game.fonts().isJapaneseAvailable();
          showFlash(jp ? Strings.Ja.LOG_DISTANT_ENEMY_MOVE : Strings.En.LOG_DISTANT_ENEMY_MOVE);
        }
      }
    }
    lastSeenEventCount = current;

    // Wave 10 W10-β-2: HP_LOW SE の境界線管理 (連続発火防止)。
    // HP > 30% → ≤ 30% の遷移時のみ発火、回復したらフラグリセット。
    var stats = game.requireRunSession().context().state().player().stats();
    boolean isLow =
        core.presentation.effect.LowHpWarning.shouldDraw(stats.currentHp(), stats.maxHp());
    if (isLow && !lowHpSeFired) {
      game.soundManager().playSe(SeKind.HP_LOW);
      lowHpSeFired = true;
    } else if (!isLow && lowHpSeFired) {
      lowHpSeFired = false;
    }
  }

  /**
   * マップカメラをプレイヤーへ追従させ、マップ端でクランプし、最後に画面シェイク (§15-5) を加える。
   *
   * <p>順序が重要: 追従位置を {@link #clampCamera} でマップ内に収めた<b>後</b>にシェイク offset を加算する。 逆順 (シェイク込み座標をクランプ)
   * だと、マップが視界より小さい層 (層 1 等) で clamp が毎フレーム カメラを中央へ固定し直し、シェイクが完全に打ち消される。
   */
  /**
   * §15-3 UI 改善: マウスクリックで手札カードを選択 (またはトグル解除)。
   *
   * <p>スクリーン座標を HUD 仮想座標 (1920x1080) に変換し、{@link HudRenderer#handCardBounds} と当たり判定する。 ポップアップ
   * (層末ノード / Elite カード選択 / ステータス) 表示中はクリック無視。
   */
  /**
   * Wave 14 W14-β: ターン終了ボタンクリック判定 (画面右下)。クリック → {@code BattleAction.EndTurn} を 発行。 ポップアップ表示中は無視。
   */
  private boolean handleEndTurnButtonClick(TurnDirector director) {
    if (!Gdx.input.justTouched()) {
      return false;
    }
    if (nodeChoice != null || eliteReward.isActive() || statusPanelOpen) {
      return false;
    }
    if (HudRenderer.endTurnButtonBounds().containsScreenInput(Gdx.input.getX(), Gdx.input.getY())) {
      director.applyPlayerAction(new BattleAction.EndTurn());
      return true;
    }
    return false;
  }

  private boolean handleHandMouseClick() {
    if (!Gdx.input.justTouched()) {
      return false;
    }
    if (nodeChoice != null || eliteReward.isActive() || statusPanelOpen) {
      return false;
    }
    float screenW = Gdx.graphics.getWidth();
    float screenH = Gdx.graphics.getHeight();
    if (screenW <= 0 || screenH <= 0) {
      return false;
    }
    float hudX = Gdx.input.getX() * (RenderLayout.SCREEN_WIDTH / screenW);
    float hudY =
        RenderLayout.SCREEN_HEIGHT - (Gdx.input.getY() * (RenderLayout.SCREEN_HEIGHT / screenH));
    int handSize =
        game.requireRunSession().context().state().player().cardPileState().hand().size();
    for (int i = 0; i < handSize && i < 9; i++) {
      Rectangle bounds = HudRenderer.handCardBounds(i);
      if (bounds.contains(hudX, hudY)) {
        playerInputs.selectCardByMouse(i);
        return true;
      }
    }
    return false;
  }

  private void updateMapCamera() {
    DungeonState s = game.requireRunSession().context().state();
    float px = s.player().position().x() * RenderLayout.TILE_SIZE + RenderLayout.TILE_SIZE / 2f;
    float py = s.player().position().y() * RenderLayout.TILE_SIZE + RenderLayout.TILE_SIZE / 2f;
    // §15-6 UI/UX: HUD パネル (画面下 300px) で下半分が隠れるため、プレイヤーを画面 Y=540 ではなく
    // 可視範囲中央 (HUD 上端 Y=300 〜 画面上端 Y=1080 の中心 Y=690) に置く。
    // camera.position.y を上にシフト = world unit で -75 (zoom 0.5 適用後の 150 screen px 相当)。
    float hudPanelHalfWorld = (RenderLayout.HUD_BOTTOM_PANEL_HEIGHT / 2f) * camera.zoom;
    camera.position.set(px, py - hudPanelHalfWorld, 0f);
    // §15-6 UI/UX: 端クランプ撤廃 — プレイヤー常に可視範囲中央、マップ外領域が見えても許容する設計。
    // ADR-19 整合: 注入済 rng を ScreenEffects 経由に渡す (同一シードでシェイク方向も再現可)。
    if (effects.isShaking()) {
      camera.position.x += effects.currentShakeOffsetX(rng);
      camera.position.y += effects.currentShakeOffsetY(rng);
    }
    camera.update();
  }

  private void transitionIfGameOver() {
    TurnPhase phase = game.requireRunSession().context().state().phase();
    if (phase == TurnPhase.GAME_OVER) {
      game.changeScreen(new GameOverScreen(game, false));
    } else if (phase == TurnPhase.RUN_CLEARED) {
      // §15-6: 最終層ボス撃破 = ラン勝利。クリア表示の GameOverScreen へ。
      game.changeScreen(new GameOverScreen(game, true));
    }
    // CLEARED は層末ノード選択待ち。updateState() で処理し、本メソッドでは画面遷移しない。
  }

  @Override
  public void resize(int width, int height) {
    // true でカメラ位置をリセット (FitViewport の黒帯を正しく配置)
    viewport.update(width, height, true);
    if (nodeChoice != null) {
      nodeChoice.resize(width, height);
    }
    if (statusPopup != null) {
      statusPopup.resize(width, height);
    }
    if (pauseMenu != null) {
      pauseMenu.resize(width, height);
    }
  }

  @Override
  public void hide() {
    // 画面非表示時にカード選択状態 + ノード選択ポップアップをリセット (再表示時の誤入力対策)
    if (playerInputs != null) {
      playerInputs.reset();
    }
    statusPanelOpen = false;
    if (nodeChoice != null) {
      nodeChoice.dispose();
      nodeChoice = null;
    }
    if (eliteReward != null) {
      eliteReward.dispose();
    }
    if (pauseMenu != null) {
      pauseMenu.dispose();
    }
  }

  @Override
  public void dispose() {
    if (batch != null) {
      batch.dispose();
    }
    if (shapes != null) {
      shapes.dispose();
    }
    if (statusPopup != null) {
      statusPopup.dispose();
      statusPopup = null;
    }
    if (nodeChoice != null) {
      nodeChoice.dispose();
      nodeChoice = null;
    }
    if (eliteReward != null) {
      eliteReward.dispose();
    }
    if (pauseMenu != null) {
      pauseMenu.dispose();
      pauseMenu = null;
    }
    if (wallTexture != null) {
      wallTexture.dispose();
      wallTexture = null;
    }
    if (floorTexture != null) {
      floorTexture.dispose();
      floorTexture = null;
    }
    if (stairsTexture != null) {
      stairsTexture.dispose();
      stairsTexture = null;
    }
    if (trapTexture != null) {
      trapTexture.dispose();
      trapTexture = null;
    }
    // Wave 10 W10-β: スプライト Texture を順次解放 (ELITE_SLIME は SLIME と同 Texture 共有のため重複 dispose 回避)
    if (playerTexture != null) {
      playerTexture.dispose();
      playerTexture = null;
    }
    if (enemyTextures != null) {
      java.util.Set<com.badlogic.gdx.graphics.Texture> seen = new java.util.HashSet<>();
      for (com.badlogic.gdx.graphics.Texture tex : enemyTextures.values()) {
        if (tex != null && seen.add(tex)) {
          tex.dispose();
        }
      }
      enemyTextures.clear();
      enemyTextures = null;
    }
    // playerInputs は LibGDX リソースを持たないので dispose 不要、reset のみ
    if (playerInputs != null) {
      playerInputs.reset();
    }
  }

  /**
   * 手札の最高 rarity に応じて CARD_DRAW SE を選ぶ純関数 (Wave 11 W11-β、CTO チェックポイント #3 を活用)。
   *
   * <p>RARE 1 枚以上 → CARD_DRAW_R / UNCOMMON 1 枚以上 → CARD_DRAW_U / それ以外 → CARD_DRAW_C。空手札も
   * CARD_DRAW_C にフォールバック (ターン頭のドロー後は通常空にならないが、空デッキ時の防衛)。
   */
  static SeKind drawSeFor(Hand hand) {
    boolean hasUncommon = false;
    for (Card c : hand.cards()) {
      CardRarity r = c.rarityOrDefault();
      if (r == CardRarity.RARE) {
        return SeKind.CARD_DRAW_R;
      }
      if (r == CardRarity.UNCOMMON) {
        hasUncommon = true;
      }
    }
    return hasUncommon ? SeKind.CARD_DRAW_U : SeKind.CARD_DRAW_C;
  }
}
