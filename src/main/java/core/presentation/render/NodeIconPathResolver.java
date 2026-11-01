package core.presentation.render;

import core.domain.entity.StatKind;
import core.domain.tree.NodeEffect;

/**
 * {@link NodeEffect} → アイコンファイルパスの解決 (純関数、§15-7 ソウルツリー描画用)。
 *
 * <p>sealed switch で 5 種 (None / StatsBonusEffect / CardGrantEffect / SlotExpandEffect /
 * LayerExtendEffect) を網羅する。新効果型を追加するとコンパイルエラーで全箇所気づける (驚き最小)。
 *
 * <p>戻り値は {@code Gdx.files.internal(path)} で読むことを想定したアセット相対パス。 ファイル欠損時は呼出側で {@code test.png}
 * へフォールバックする (`CardImageRegistry` と同思想)。
 *
 * <p>{@link NodeEffect.CardGrantEffect} については {@code null} を返す ({@code CardImageRegistry} 経由で
 * カード画像を直接取得するため)。
 */
public final class NodeIconPathResolver {

  private static final String STATS_DIR = "icons/stats/";
  private static final String FRAME_PATH = "icons/frame.png";
  private static final String CENTER_PATH = "icons/center.png";

  private NodeIconPathResolver() {}

  /**
   * NodeEffect に対応するアイコンパスを返す。
   *
   * @param effect ソウルツリーノードの効果型
   * @return アイコンの classpath 相対パス、CardGrantEffect の場合は {@code null}
   */
  public static String resolve(NodeEffect effect) {
    return switch (effect) {
      case NodeEffect.CardGrantEffect ignored -> null;
      case NodeEffect.StatsBonusEffect s ->
          STATS_DIR + statFilename(s.bonus().dominantStat()) + ".png";
      case NodeEffect.SlotExpandEffect ignored -> FRAME_PATH;
      case NodeEffect.RetentionCapacityUpEffect ignored -> FRAME_PATH;
      case NodeEffect.None ignored -> CENTER_PATH;
      case NodeEffect.LayerExtendEffect ignored -> "icons/layer_extend.png";
    };
  }

  /** StatKind を画像ファイル名 (拡張子なし) に変換。 */
  private static String statFilename(StatKind stat) {
    return switch (stat) {
      case HP -> "hp";
      case SPEED -> "speed";
      case PHYSICAL_ATTACK -> "phys_atk";
      case MAGICAL_ATTACK -> "mag_atk";
      case PHYSICAL_DEFENSE -> "phys_def";
      case MAGICAL_DEFENSE -> "mag_def";
    };
  }
}
