package core.presentation.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.domain.card.CardId;
import core.domain.layer.EventKind;
import core.domain.layer.LayerEndNode;
import core.domain.layer.NodeResolveContext;
import core.infrastructure.bootstrap.InitialStateFactory;
import org.junit.jupiter.api.Test;

/**
 * {@link LayerEndNodeLabels} の単体テスト (Wave 8 W8-α)。
 *
 * <p>旧 {@code LayerEndNode#displayName(NodeResolveContext)} (domain 層) で行っていた表示文字列検証を presentation
 * 層に移動。Ja/En の両セットで各 variant のラベル組み立てが期待通りであることを確認する。
 */
class LayerEndNodeLabelsTest {

  /** テスト用 NodeResolveContext: cards / equipments の解決は本物のマスタを使う。 */
  private static NodeResolveContext fullContext() {
    return new NodeResolveContext(
        InitialStateFactory::resolveCard, id -> InitialStateFactory.equipmentCatalog().get(id));
  }

  private static CardId sampleCardId() {
    return CardId.of("strong_strike");
  }

  // ---------------- HpMaxUp ----------------

  @Test
  void hpMaxUpJa() {
    String label = LayerEndNodeLabels.labelOf(new LayerEndNode.HpMaxUp(5), fullContext(), true);
    assertEquals("HP +5", label);
  }

  @Test
  void hpMaxUpEn() {
    String label = LayerEndNodeLabels.labelOf(new LayerEndNode.HpMaxUp(5), fullContext(), false);
    assertEquals("HP +5", label);
  }

  // ---------------- SpeedUp ----------------

  @Test
  void speedUpJa() {
    assertEquals(
        "速度 +1", LayerEndNodeLabels.labelOf(new LayerEndNode.SpeedUp(1), fullContext(), true));
  }

  @Test
  void speedUpEn() {
    assertEquals(
        "Speed +1", LayerEndNodeLabels.labelOf(new LayerEndNode.SpeedUp(1), fullContext(), false));
  }

  // ---------------- Rest ----------------

  @Test
  void restJa() {
    assertEquals(
        "HP を最大値の 1/3 回復",
        LayerEndNodeLabels.labelOf(new LayerEndNode.Rest(), fullContext(), true));
  }

  @Test
  void restEn() {
    assertEquals(
        "Heal 1/3 of max HP",
        LayerEndNodeLabels.labelOf(new LayerEndNode.Rest(), fullContext(), false));
  }

  // ---------------- Shop ----------------

  @Test
  void shopJaIncludesCardNameAndCost() {
    String label =
        LayerEndNodeLabels.labelOf(new LayerEndNode.Shop(5, sampleCardId()), fullContext(), true);
    assertTrue(label.contains("ショップ"), "Ja: 'ショップ' プレフィックス");
    assertTrue(label.contains("5"), "Ja: goldCost が含まれる");
    assertTrue(
        label.contains(InitialStateFactory.resolveCard(sampleCardId()).displayName()),
        "Ja: resolver でカード名が解決される");
  }

  @Test
  void shopEnIncludesCardNameAndCost() {
    String label =
        LayerEndNodeLabels.labelOf(new LayerEndNode.Shop(5, sampleCardId()), fullContext(), false);
    assertTrue(label.contains("Shop"), "En: 'Shop' プレフィックス");
    assertTrue(label.contains("5"));
  }

  // ---------------- Event ----------------

  @Test
  void eventHealingSpringJa() {
    String label =
        LayerEndNodeLabels.labelOf(
            LayerEndNode.Event.of(EventKind.HEALING_SPRING), fullContext(), true);
    assertEquals("治療の泉 (HP +20 / ソウル -10)", label);
  }

  @Test
  void eventGoldenChestJa() {
    String label =
        LayerEndNodeLabels.labelOf(
            LayerEndNode.Event.of(EventKind.GOLDEN_CHEST), fullContext(), true);
    assertEquals("黄金の宝箱 (金貨 +50)", label);
  }

  @Test
  void eventSoulShrineJa() {
    String label =
        LayerEndNodeLabels.labelOf(
            LayerEndNode.Event.of(EventKind.SOUL_SHRINE), fullContext(), true);
    assertEquals("ソウルの祠 (ソウル +30 / HP -5)", label);
  }

  @Test
  void eventHealingSpringEn() {
    String label =
        LayerEndNodeLabels.labelOf(
            LayerEndNode.Event.of(EventKind.HEALING_SPRING), fullContext(), false);
    assertEquals("Healing Spring (HP +20 / Soul -10)", label);
  }

  // ---------------- ShopEquipment ----------------

  @Test
  void shopEquipmentJaIncludesEquipmentNameAndCost() {
    var eqId = InitialStateFactory.equipmentCatalog().all().get(0).id();
    String eqName = InitialStateFactory.equipmentCatalog().all().get(0).displayName();
    String label =
        LayerEndNodeLabels.labelOf(new LayerEndNode.ShopEquipment(15, eqId), fullContext(), true);
    assertTrue(label.contains("装備購入"));
    assertTrue(label.contains("15"));
    assertTrue(label.contains(eqName), "Ja: 装備名が含まれる");
  }

  @Test
  void shopEquipmentEnIncludesEquipmentNameAndCost() {
    var eqId = InitialStateFactory.equipmentCatalog().all().get(0).id();
    String label =
        LayerEndNodeLabels.labelOf(new LayerEndNode.ShopEquipment(15, eqId), fullContext(), false);
    assertTrue(label.contains("Equip"));
    assertTrue(label.contains("15"));
  }

  // ---------------- Ja / En 差異 ----------------

  @Test
  void jaAndEnLabelsDifferForRestAndEventVariants() {
    // Rest / Event は完全に変わる
    assertNotEquals(
        LayerEndNodeLabels.labelOf(new LayerEndNode.Rest(), fullContext(), true),
        LayerEndNodeLabels.labelOf(new LayerEndNode.Rest(), fullContext(), false));
    for (EventKind kind : EventKind.values()) {
      assertNotEquals(
          LayerEndNodeLabels.labelOf(LayerEndNode.Event.of(kind), fullContext(), true),
          LayerEndNodeLabels.labelOf(LayerEndNode.Event.of(kind), fullContext(), false),
          "EventKind " + kind + " の Ja / En ラベルは異なる");
    }
  }
}
