package core.presentation.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Disposable;
import core.domain.card.CardTag;
import core.domain.layer.LayerEndNode;
import core.presentation.render.Strings;
import core.presentation.window.NodeChoicePopup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 強化個体 (Elite) 撃破時のカード追加報酬 UI のライフサイクルを一元管理するオーケストレータ (Wave 4 W4-γ)。
 *
 * <p>DungeonScreen から以下を切り出し、render パイプラインをさらに薄くする:
 *
 * <ul>
 *   <li>{@link NodeChoicePopup} の生成 / 入力処理 / dispose
 *   <li>ATTACK カード 3 択抽選 (攻撃カード不足時は他タグで補填)
 *   <li>選択確定後の {@link DddGame#applyEliteCardReward} 呼出 + flash 通知
 * </ul>
 *
 * <p><b>依存関係</b>: {@link DddGame} (カードマスタ取得 / 報酬適用)、 {@link Random} (カード抽選)、{@link ScreenEffects}
 * (flash 通知のみ)。それ以外の副作用はドメイン / application 層に閉じる。
 */
public final class EliteRewardOrchestrator implements Disposable {

  private final DddGame game;
  private final Random rng;
  private final ScreenEffects effects;

  /** §15-3 / §15-6: 強化個体撃破時のカード追加 UI (1 ノード選択 = 1 枚 DrawPile 追加)。 */
  private NodeChoicePopup popup;

  /** 開いた同一フレームの入力が報酬選択に流用されるのを防ぐガードフラグ。 */
  private boolean openedThisFrame;

  /**
   * @param game DddGame (カードマスタ取得 / 報酬適用 / フォント)
   * @param rng 乱数源 (ADR-19: 注入済みインスタンスを使い、同一シードで再現可能にする)
   * @param effects flash 通知のみ使用する (shake / popup 生成は行わない)
   */
  public EliteRewardOrchestrator(DddGame game, Random rng, ScreenEffects effects) {
    this.game = game;
    this.rng = rng;
    this.effects = effects;
  }

  // -------------------------------------------------------------------------
  // public API
  // -------------------------------------------------------------------------

  /**
   * 強化個体撃破イベント受信時に呼ぶ。popup が未生成の場合のみ {@link NodeChoicePopup} を生成する。
   *
   * <p>既に popup が表示中 (前回報酬未選択) の場合は no-op とし、二重生成を防ぐ。
   */
  public void triggerOnEliteDefeat() {
    if (popup == null) {
      popup = createEliteCardChoicePopup();
      openedThisFrame = true;
    }
  }

  /**
   * 毎フレームの入力処理。1〜3 数字キーで選択 → {@link DddGame#applyEliteCardReward} 呼出 + flash 通知 → popup dispose。
   *
   * <p>popup が null (非アクティブ) の場合、または開いたばかりのフレームでは何もせず false を返す。
   *
   * @return 報酬を選択して popup を閉じたフレームのみ true
   */
  public boolean handleInput() {
    if (popup == null) {
      return false;
    }
    if (openedThisFrame) {
      openedThisFrame = false;
      return false;
    }
    for (int i = 0; i < NodeChoicePopup.CHOICE_COUNT; i++) {
      if (Gdx.input.isKeyJustPressed(numKey(i))) {
        popup.select(i);
      }
    }
    var chosen = popup.consume();
    if (chosen.isEmpty()) {
      return false;
    }
    LayerEndNode choice = chosen.get();
    game.applyEliteCardReward(choice);
    popup.dispose();
    popup = null;
    if (choice instanceof LayerEndNode.Shop shop) {
      boolean jp = game.fonts().isJapaneseAvailable();
      // Wave 3 Task A: Shop は CardId 保持なので cardCatalog 経由で displayName を解決
      String cardName = game.cardCatalog().get(shop.cardId()).displayName();
      effects.showFlash(
          (jp ? Strings.Ja.CARD_REWARD_GAINED_FORMAT : Strings.En.CARD_REWARD_GAINED_FORMAT)
              .formatted(cardName));
    }
    return true;
  }

  /** popup が表示中 (非 null) なら true。 */
  public boolean isActive() {
    return popup != null;
  }

  /**
   * popup を描画する。popup が null (非アクティブ) の場合は no-op。
   *
   * @param delta フレーム経過秒数
   */
  public void render(float delta) {
    if (popup != null) {
      popup.render(delta);
    }
  }

  /** popup を dispose して null 化する。{@link Disposable#dispose()} 準拠。 すでに null の場合は no-op。 */
  @Override
  public void dispose() {
    openedThisFrame = false;
    if (popup != null) {
      popup.dispose();
      popup = null;
    }
  }

  // -------------------------------------------------------------------------
  // private helpers
  // -------------------------------------------------------------------------

  /**
   * Elite 撃破時のカード追加候補 (3 種) を {@link NodeChoicePopup} で生成する。
   *
   * <p>候補プールはカードマスタ (cards.json) の<b>攻撃カード</b>からシャッフルで 3 つ選び、それぞれ {@link
   * LayerEndNode.Shop}(goldCost=0, card) でラップ。Gold 0 = 無料カード追加。攻撃カードに限定するのは、撃破報酬を 手応えのある 3
   * 択にし「移動・バフだけのハズレ 3 択」を防ぐため (他タグはショップノードで入手可能)。 攻撃カードが CHOICE_COUNT (3) 未満なら他タグから補填し
   * IndexOutOfBoundsException を防ぐ。
   */
  private NodeChoicePopup createEliteCardChoicePopup() {
    List<core.domain.card.Card> attackPool =
        new ArrayList<>(
            game.cardCatalog().all().stream().filter(c -> c.tag() == CardTag.ATTACK).toList());
    Collections.shuffle(attackPool, rng);
    // §UI 改善: ATTACK カードが CHOICE_COUNT (3) 未満なら他タグから補填する (IndexOutOfBoundsException 対策)。
    List<core.domain.card.Card> rewards = new ArrayList<>(attackPool);
    if (rewards.size() < NodeChoicePopup.CHOICE_COUNT) {
      List<core.domain.card.Card> fallbackPool =
          new ArrayList<>(
              game.cardCatalog().all().stream().filter(c -> c.tag() != CardTag.ATTACK).toList());
      Collections.shuffle(fallbackPool, rng);
      for (core.domain.card.Card c : fallbackPool) {
        if (rewards.size() >= NodeChoicePopup.CHOICE_COUNT) {
          break;
        }
        rewards.add(c);
      }
    }
    List<LayerEndNode> choices = new ArrayList<>();
    int n = Math.min(NodeChoicePopup.CHOICE_COUNT, rewards.size());
    for (int i = 0; i < n; i++) {
      // Wave 3 Task A: Shop は CardId 保持に変更されたため card.id() を渡す
      choices.add(new LayerEndNode.Shop(0, rewards.get(i).id()));
    }
    boolean jp = game.fonts().isJapaneseAvailable();
    String title = jp ? Strings.Ja.ELITE_CARD_REWARD_TITLE : Strings.En.ELITE_CARD_REWARD_TITLE;
    return new NodeChoicePopup(
        game.fonts().large(), title, List.copyOf(choices), game.nodeResolveContext(), jp);
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
}
