package core.domain.layer;

import core.domain.card.Card;
import core.domain.card.CardId;
import core.domain.card.CardPileState;
import core.domain.card.DrawPile;
import core.domain.entity.Player;
import core.domain.entity.Stats;
import core.domain.equipment.EquipmentId;
import core.domain.meta.Gold;
import core.domain.meta.Soul;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 層末ノードの効果 (§15-8 / E-6)。階段踏破後にプレイヤーが 3 択から 1 つ選ぶノードを表現する。
 *
 * <p>各実装は純関数 {@link #apply(Player, NodeResolveContext)} で新 Player を返す (副作用分離)。
 *
 * <p>Wave 3 Task A: Shop は {@link Card} 直接保持から {@link CardId} 保持に変更し、ドメイン層が Card 実装に依存しない構造に統一。
 *
 * <p>Wave 8 W8-α: 各 variant から {@code displayName(NodeResolveContext)} を撤去し、表示ラベル解決は presentation
 * 層の {@code LayerEndNodeLabels} に委譲。Event の {@code displayLabel: String} field も {@link EventKind}
 * enum に置換し、ドメイン層から日本語汚染を完全排除。
 */
public sealed interface LayerEndNode
    permits LayerEndNode.HpMaxUp,
        LayerEndNode.SpeedUp,
        LayerEndNode.Rest,
        LayerEndNode.Shop,
        LayerEndNode.Event,
        LayerEndNode.ShopEquipment {

  /**
   * 効果を適用した新しい Player を返す (純関数)。
   *
   * @param player 適用前の Player
   * @param context Card / Equipment 解決コンテキスト (Shop など resolver を必要とする variant が利用)
   */
  Player apply(Player player, NodeResolveContext context);

  /**
   * 指定された Player がこのノードのコストを支払えるかを返す。
   *
   * @param player 判定対象の Player
   * @return 支払い可能 (またはコストなし) なら true
   */
  default boolean canAfford(Player player) {
    Objects.requireNonNull(player, "player");
    return true;
  }

  /**
   * 最大 HP を {@code amount} 加算する。現在 HP も同量加算し「上限上昇分が即座に空きスロットを埋める」体験にする (Slay the Spire の永続バフと同型)。
   *
   * <p>amount は 1 以上 (0 や負数は「強化」として無意味)。
   */
  record HpMaxUp(int amount) implements LayerEndNode {
    public HpMaxUp {
      if (amount < 1) {
        throw new IllegalArgumentException("amount must be >= 1 (got " + amount + ")");
      }
    }

    @Override
    public Player apply(Player player, NodeResolveContext context) {
      // context は未使用 (signature 追従のみ): HpMaxUp は内部状態だけで完結する純関数
      return player.withStats(player.stats().withMaxHpRaised(amount));
    }
  }

  /**
   * 速度を {@code amount} 加算する (= 1 ターンの最大 AP が増える、§15-4)。
   *
   * <p>速度は最強ステ (DPT を直接押し上げる)、§15-7 のソウルツリーでも高コスト設計。本 record では amount は 1 以上を強制 (層末ノードの強化値は通常 +1)。
   */
  record SpeedUp(int amount) implements LayerEndNode {
    public SpeedUp {
      if (amount < 1) {
        throw new IllegalArgumentException("amount must be >= 1 (got " + amount + ")");
      }
    }

    @Override
    public Player apply(Player player, NodeResolveContext context) {
      // context は未使用 (signature 追従のみ)
      return player.withStats(player.stats().withSpeedRaised(amount));
    }
  }

  /**
   * 休憩ノード (Wave 15 W15-γ / #2: 無償全回復ノードのバランス是正)。現在 HP を **最大 HP の 30% (切り上げ、最低 1)** 回復する。
   *
   * <p>旧仕様は `stats.healed(maxHp)` で確定的に全回復、ソウル/金貨消費ゼロのため「3 択に出れば無条件で勝つ最強選択肢」と 化していた。Wave 15
   * で「割合回復に制限」により、ノード選択の意思決定 (回復 vs 強化 vs ショップ vs イベント) が成立する。
   *
   * <p>パラメータなしの marker record。割合は実装定数 {@code REST_HEAL_RATIO_DENOMINATOR=3} で固定 (= 30% 切り上げ)。
   */
  record Rest() implements LayerEndNode {

    /** Wave 15 W15-γ: 30% 回復 = maxHp / 3 切り上げ。 */
    private static final int REST_HEAL_RATIO_DENOMINATOR = 3;

    @Override
    public Player apply(Player player, NodeResolveContext context) {
      // context は未使用 (signature 追従のみ)
      Stats s = player.stats();
      int healAmount = Math.max(1, s.maxHp() / REST_HEAL_RATIO_DENOMINATOR);
      return player.withStats(s.healed(healAmount));
    }
  }

  /**
   * ショップノード (§15-8 / §15-9 縮退実装)。{@code goldCost} 分の金貨を消費して {@code cardId} で示されるカードを DrawPile に 1
   * 枚追加する。完成仕様の「装備変更 UI + デッキ再生成」(ADR-26) は M2 送り、本セッションでは 単発カード追加に縮退。
   *
   * <p>Wave 3 Task A: 旧 {@code Shop(int, Card)} を {@code Shop(int, CardId)} に変更。ドメイン層が Card
   * 実装に依存しない 設計に統一し、ID 保持 + resolver 解決パターンに揃える。
   *
   * <p>Gold 不足時は silent fail (apply が引数の Player をそのまま返す、ActionRejected はドメイン層で発火しない)。
   */
  record Shop(int goldCost, CardId cardId) implements LayerEndNode {
    public Shop {
      if (goldCost < 0) {
        throw new IllegalArgumentException("goldCost must be non-negative: " + goldCost);
      }
      Objects.requireNonNull(cardId, "cardId");
    }

    @Override
    public boolean canAfford(Player player) {
      Objects.requireNonNull(player, "player");
      return player.gold().amount() >= goldCost;
    }

    @Override
    public Player apply(Player player, NodeResolveContext context) {
      Objects.requireNonNull(context, "context");
      if (!canAfford(player)) {
        return player; // silent fail
      }
      Card granted = context.cards().apply(cardId);
      Objects.requireNonNull(granted, "context.cards() returned null for " + cardId.value());
      Player afterGold = goldCost > 0 ? player.spendGold(new Gold(goldCost)) : player;
      CardPileState pile = afterGold.cardPileState();
      List<Card> newDrawCards = new ArrayList<>(pile.drawPile().cards());
      newDrawCards.add(granted);
      return afterGold.withCardPileState(
          new CardPileState(new DrawPile(newDrawCards), pile.hand(), pile.discardPile()));
    }
  }

  /**
   * イベントノード (§15-8)。Soul / HP / Gold を相対変動させる即時単発効果。「ソウルの祠 (Soul +30 / HP -5)」のような
   * リスク・リワードのトレードオフを表現する。
   *
   * <p>Wave 8 W8-α: 旧 {@code displayLabel: String} field を {@link EventKind} に置換。compact
   * constructor で kind と delta の整合性を検証し、外部からの不整合呼出 (例: {@code new Event(99, 99, 99,
   * EventKind.HEALING_SPRING)}) を早期検出する。
   *
   * <p>各 delta は kind のデフォルト値と一致しなければならない:
   *
   * <ul>
   *   <li>{@code soulDelta} == {@code kind.defaultSoulDelta()}
   *   <li>{@code hpDelta} == {@code kind.defaultHpDelta()}
   *   <li>{@code goldDelta} == {@code kind.defaultGoldDelta()}
   * </ul>
   *
   * <p>削減用 factory: {@link #of(EventKind)} で kind だけ渡せば自動で delta を埋める。
   */
  record Event(int soulDelta, int hpDelta, int goldDelta, EventKind kind) implements LayerEndNode {
    public Event {
      Objects.requireNonNull(kind, "kind");
      if (soulDelta != kind.defaultSoulDelta()
          || hpDelta != kind.defaultHpDelta()
          || goldDelta != kind.defaultGoldDelta()) {
        throw new IllegalArgumentException(
            "Event delta values must match EventKind defaults: got soul=%d hp=%d gold=%d for kind=%s (expected soul=%d hp=%d gold=%d)"
                .formatted(
                    soulDelta,
                    hpDelta,
                    goldDelta,
                    kind,
                    kind.defaultSoulDelta(),
                    kind.defaultHpDelta(),
                    kind.defaultGoldDelta()));
      }
    }

    /** kind のデフォルト delta を自動で埋めた Event を返す (Wave 8 W8-α 推奨 factory)。 */
    public static Event of(EventKind kind) {
      Objects.requireNonNull(kind, "kind");
      return new Event(
          kind.defaultSoulDelta(), kind.defaultHpDelta(), kind.defaultGoldDelta(), kind);
    }

    @Override
    public boolean canAfford(Player player) {
      Objects.requireNonNull(player, "player");
      if (soulDelta < 0 && player.soul().amount() < -soulDelta) {
        return false;
      }
      if (goldDelta < 0 && player.gold().amount() < -goldDelta) {
        return false;
      }
      return true;
    }

    @Override
    public Player apply(Player player, NodeResolveContext context) {
      // context は未使用 (signature 追従のみ)。
      // §UI 改善 Wave 3 Task C: ソウル/金貨が不足する負値 delta は silent fail (player そのまま返す)。
      if (!canAfford(player)) {
        return player;
      }
      Player after = player;
      if (soulDelta > 0) {
        after = after.addSoul(new Soul(soulDelta));
      } else if (soulDelta < 0) {
        after = after.subtractSoul(new Soul(-soulDelta));
      }
      if (hpDelta != 0) {
        Stats currentStats = after.stats();
        Stats newStats =
            hpDelta > 0 ? currentStats.healed(hpDelta) : currentStats.damaged(-hpDelta);
        after = after.withStats(newStats);
      }
      if (goldDelta > 0) {
        after = after.addGold(new Gold(goldDelta));
      } else if (goldDelta < 0) {
        after = after.spendGold(new Gold(-goldDelta));
      }
      return after;
    }
  }

  /**
   * 装備購入ノード (§15-8 / §15-9 / Wave 3 Task B)。{@code goldCost} 分の金貨を消費し、{@code equipmentId} で示される装備を
   * 「次ラン以降のロードアウトへ装着」する。
   *
   * <p>ドメイン層の {@link Player} record は装備ロードアウトを保持しない設計 (装備管理は {@code DddGame.loadout} に 集約、ADR-25 /
   * ADR-26)。そのため本 record の {@link #apply} は<b>純関数として Gold 消費のみ</b>を行い、 実際の装備装着 (loadout への反映) は呼出元
   * ({@code DddGame.resolveLayerEndChoice}) で {@code apply 前後で Gold が変化した = 購入成功} を検出した時に {@code
   * equipInLoadout} を呼ぶ責務分割とする。
   *
   * <p>Gold 不足時は silent fail (Shop と同型: apply が引数の Player をそのまま返す)。
   */
  record ShopEquipment(int goldCost, EquipmentId equipmentId) implements LayerEndNode {
    public ShopEquipment {
      if (goldCost < 0) {
        throw new IllegalArgumentException("goldCost must be non-negative: " + goldCost);
      }
      Objects.requireNonNull(equipmentId, "equipmentId");
    }

    @Override
    public boolean canAfford(Player player) {
      Objects.requireNonNull(player, "player");
      return player.gold().amount() >= goldCost;
    }

    @Override
    public Player apply(Player player, NodeResolveContext context) {
      Objects.requireNonNull(context, "context");
      if (!canAfford(player)) {
        return player; // silent fail
      }
      // 装着 (loadout 反映) は presentation 層の責務。ここでは Gold 消費のみで純関数性を維持する。
      return goldCost > 0 ? player.spendGold(new Gold(goldCost)) : player;
    }
  }
}
