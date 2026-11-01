package core.presentation.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.domain.card.CardId;
import core.domain.entity.StatKind;
import core.domain.equipment.StatsBonus;
import core.domain.tree.NodeEffect;
import org.junit.jupiter.api.Test;

/**
 * {@link NodeIconPathResolver} の単体テスト (Wave 7 W7-γ、§15-7)。
 *
 * <p>5 種の {@link NodeEffect} それぞれが正しいアイコンパスを返すことを検証する。 NodeEffect は sealed なので新型追加でコンパイルエラー +
 * 本テストで挙動が固定される。
 */
class NodeIconPathResolverTest {

  @Test
  void resolve_noneEffectReturnsCenterPath() {
    String path = NodeIconPathResolver.resolve(NodeEffect.None.INSTANCE);
    assertEquals("icons/center.png", path, "None → center.png");
  }

  @Test
  void resolve_cardGrantEffectReturnsNull() {
    NodeEffect effect = new NodeEffect.CardGrantEffect(CardId.of("zangeki"));
    assertNull(
        NodeIconPathResolver.resolve(effect), "CardGrantEffect → null (CardImageRegistry 経由で取得)");
  }

  @Test
  void resolve_slotExpandEffectReturnsFramePath() {
    String path = NodeIconPathResolver.resolve(new NodeEffect.SlotExpandEffect(1));
    assertEquals("icons/frame.png", path, "SlotExpandEffect → frame.png");
  }

  @Test
  void resolve_layerExtendEffectFallsBackToCenter() {
    String path = NodeIconPathResolver.resolve(new NodeEffect.LayerExtendEffect(1));
    assertEquals("icons/layer_extend.png", path, "LayerExtend → layer_extend.png");
  }

  @Test
  void resolve_statsBonusHpReturnsHpIcon() {
    // StatsBonus(maxHp=5, ...) で dominantStat() = HP
    StatsBonus bonus = new StatsBonus(5, 0, 0, 0, 0, 0);
    String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
    assertEquals("icons/stats/hp.png", path, "HP ボーナス → hp.png");
  }

  @Test
  void resolve_statsBonusSpeedReturnsSpeedIcon() {
    StatsBonus bonus = new StatsBonus(0, 1, 0, 0, 0, 0);
    String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
    assertEquals("icons/stats/speed.png", path);
  }

  @Test
  void resolve_statsBonusPhysicalAttackReturnsPhysAtkIcon() {
    StatsBonus bonus = new StatsBonus(0, 0, 1, 0, 0, 0);
    String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
    assertEquals("icons/stats/phys_atk.png", path);
  }

  @Test
  void resolve_statsBonusMagicalAttackReturnsMagAtkIcon() {
    StatsBonus bonus = new StatsBonus(0, 0, 0, 1, 0, 0);
    String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
    assertEquals("icons/stats/mag_atk.png", path);
  }

  @Test
  void resolve_statsBonusPhysicalDefenseReturnsPhysDefIcon() {
    StatsBonus bonus = new StatsBonus(0, 0, 0, 0, 1, 0);
    String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
    assertEquals("icons/stats/phys_def.png", path);
  }

  @Test
  void resolve_statsBonusMagicalDefenseReturnsMagDefIcon() {
    StatsBonus bonus = new StatsBonus(0, 0, 0, 0, 0, 1);
    String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
    assertEquals("icons/stats/mag_def.png", path);
  }

  @Test
  void resolve_allStatKindsHaveValidFilenameMapping() {
    // 各 StatKind についてファイル名が「icons/stats/*.png」の形式で返ること
    for (StatKind stat : StatKind.values()) {
      StatsBonus bonus =
          switch (stat) {
            case HP -> new StatsBonus(1, 0, 0, 0, 0, 0);
            case SPEED -> new StatsBonus(0, 1, 0, 0, 0, 0);
            case PHYSICAL_ATTACK -> new StatsBonus(0, 0, 1, 0, 0, 0);
            case MAGICAL_ATTACK -> new StatsBonus(0, 0, 0, 1, 0, 0);
            case PHYSICAL_DEFENSE -> new StatsBonus(0, 0, 0, 0, 1, 0);
            case MAGICAL_DEFENSE -> new StatsBonus(0, 0, 0, 0, 0, 1);
          };
      String path = NodeIconPathResolver.resolve(new NodeEffect.StatsBonusEffect(bonus));
      assertTrue(
          path.startsWith("icons/stats/") && path.endsWith(".png"),
          stat.name() + " のパス形式 (got=" + path + ")");
    }
  }
}
