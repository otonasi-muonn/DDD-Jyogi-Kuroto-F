package core.presentation.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import core.infrastructure.audio.SeKind;
import core.infrastructure.save.Settings;
import core.infrastructure.save.ThemeMode;
import core.infrastructure.save.UiPreset;
import core.presentation.render.Fonts;
import core.presentation.render.RenderLayout;
import core.presentation.render.Strings;
import core.presentation.window.ConfirmationDialog;
import java.util.Optional;

/**
 * 設定画面 (§15-1 / §15-8)。
 *
 * <p>BGM 音量・SE 音量・フルスクリーン・UI プリセットを変更できる。 変更は即時 {@link DddGame#applySettings(Settings)} で反映し、ESC で
 * タイトルへ戻る際に自動保存する。
 *
 * <p>操作: ↑↓ / WS で項目移動、←→ / AD で値変更、ESC で戻る。
 */
public final class SettingsScreen extends ScreenAdapter {

  /** 設定値として左右キーで変更可能な項目数 (PauseMenuController の設定サブ画面と共用)。 */
  static final int SETTING_ITEM_COUNT = 5;

  /** SettingsScreen 全体の項目数 (5 設定項目 + セーブデータ初期化)。 */
  private static final int ITEM_COUNT = 6;

  static final int ITEM_BGM = 0;
  static final int ITEM_SE = 1;
  static final int ITEM_FULLSCREEN = 2;
  static final int ITEM_UI_PRESET = 3;
  // Wave 17 W17-γ / #8: テーマ (ライト / ダーク 2 トグル)
  static final int ITEM_THEME = 4;
  // 来場者交代用のセーブデータ初期化
  private static final int ITEM_RESET_SAVE = 5;

  /** 音量の変更ステップ。 */
  private static final float VOLUME_STEP = 0.1f;

  private final DddGame game;
  private OrthographicCamera camera;
  private Viewport viewport;
  private SpriteBatch batch;

  /** 現在選択されている項目インデックス。 */
  private int selectedItem = 0;

  /** 編集中の設定 (ESC で戻る前に保存)。 */
  private Settings current;

  /** セーブデータ初期化の確認ダイアログ (非表示時は null)。 */
  private ConfirmationDialog resetConfirm;

  /** セーブデータ初期化に失敗したときのエラー表示フラグ。 */
  private boolean resetFailed;

  public SettingsScreen(DddGame game) {
    this.game = game;
  }

  @Override
  public void show() {
    camera = new OrthographicCamera();
    viewport = new FitViewport(RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT, camera);
    batch = new SpriteBatch();
    current = game.persistence().settings();
  }

  @Override
  public void render(float delta) {
    if (resetConfirm == null && handleInput()) {
      return; // 画面遷移済み: dispose された batch で描画しない
    }

    ScreenUtils.clear(0.05f, 0.05f, 0.10f, 1f);
    viewport.apply();
    batch.setProjectionMatrix(camera.combined);

    Fonts fonts = game.fonts();
    boolean jp = fonts.isJapaneseAvailable();
    BitmapFont large = fonts.large();
    BitmapFont title = fonts.title();

    batch.begin();

    // タイトル
    title.setColor(Color.WHITE);
    title.draw(
        batch,
        jp ? Strings.Ja.SETTINGS_TITLE : Strings.En.SETTINGS_TITLE,
        RenderLayout.SETTINGS_TITLE_X,
        RenderLayout.SETTINGS_TITLE_Y);

    // 設定項目リスト
    drawItem(batch, large, jp, ITEM_BGM, RenderLayout.SETTINGS_ROW_TOP_Y);
    drawItem(
        batch,
        large,
        jp,
        ITEM_SE,
        RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT);
    drawItem(
        batch,
        large,
        jp,
        ITEM_FULLSCREEN,
        RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT * 2);
    drawItem(
        batch,
        large,
        jp,
        ITEM_UI_PRESET,
        RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT * 3);
    // Wave 17 W17-γ / #8 テーマ項目
    drawItem(
        batch,
        large,
        jp,
        ITEM_THEME,
        RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT * 4);
    // 来場者交代用セーブデータ初期化
    drawItem(
        batch,
        large,
        jp,
        ITEM_RESET_SAVE,
        RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT * 5);

    if (resetFailed) {
      large.setColor(new Color(1f, 0.35f, 0.35f, 1f));
      large.draw(
          batch,
          jp ? Strings.Ja.RESET_SAVE_FAILED_MESSAGE : Strings.En.RESET_SAVE_FAILED_MESSAGE,
          RenderLayout.SETTINGS_LABEL_X,
          RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT * 6 - 16);
    }

    // 操作ヒント
    large.setColor(Color.GRAY);
    large.draw(
        batch,
        jp ? Strings.Ja.SETTINGS_HINT : Strings.En.SETTINGS_HINT,
        RenderLayout.SETTINGS_LABEL_X,
        RenderLayout.SETTINGS_HINT_Y);

    batch.end();

    if (resetConfirm != null) {
      resetConfirm.render(delta);
      Optional<Boolean> confirmed = resetConfirm.consume();
      if (confirmed.isPresent()) {
        resetConfirm.dispose();
        resetConfirm = null;
        if (confirmed.get()) {
          game.soundManager().playSe(SeKind.BUTTON_DECISION);
          if (game.resetAllProgress()) {
            game.changeScreen(new TitleScreen(game));
            return;
          }
          resetFailed = true;
        }
      }
    }
  }

  private void drawItem(SpriteBatch batch, BitmapFont font, boolean jp, int item, int y) {
    boolean selected = item == selectedItem;
    String label = getLabel(jp, item);
    String value = getValue(current, jp, item);

    font.setColor(selected ? Color.YELLOW : Color.LIGHT_GRAY);
    String prefix = selected ? "> " : "  ";
    font.draw(batch, prefix + label, RenderLayout.SETTINGS_LABEL_X, y);

    font.setColor(selected ? Color.WHITE : Color.LIGHT_GRAY);
    font.draw(batch, value, RenderLayout.SETTINGS_VALUE_X, y);
  }

  static String getLabel(boolean jp, int item) {
    return switch (item) {
      case ITEM_BGM -> jp ? Strings.Ja.SETTINGS_BGM_VOLUME : Strings.En.SETTINGS_BGM_VOLUME;
      case ITEM_SE -> jp ? Strings.Ja.SETTINGS_SE_VOLUME : Strings.En.SETTINGS_SE_VOLUME;
      case ITEM_FULLSCREEN -> jp ? Strings.Ja.SETTINGS_FULLSCREEN : Strings.En.SETTINGS_FULLSCREEN;
      case ITEM_UI_PRESET -> jp ? Strings.Ja.SETTINGS_UI_PRESET : Strings.En.SETTINGS_UI_PRESET;
      case ITEM_THEME -> jp ? Strings.Ja.SETTINGS_THEME : Strings.En.SETTINGS_THEME;
      case ITEM_RESET_SAVE -> jp ? Strings.Ja.SETTINGS_RESET_SAVE : Strings.En.SETTINGS_RESET_SAVE;
      default -> "";
    };
  }

  static String getValue(Settings settings, boolean jp, int item) {
    return switch (item) {
      case ITEM_BGM -> "%.0f%%".formatted(settings.bgmVolume() * 100);
      case ITEM_SE -> "%.0f%%".formatted(settings.seVolume() * 100);
      case ITEM_FULLSCREEN ->
          settings.fullscreen()
              ? (jp ? Strings.Ja.SETTINGS_ON : Strings.En.SETTINGS_ON)
              : (jp ? Strings.Ja.SETTINGS_OFF : Strings.En.SETTINGS_OFF);
      case ITEM_UI_PRESET ->
          jp ? settings.uiPreset().displayNameJa() : settings.uiPreset().displayNameEn();
      case ITEM_THEME ->
          settings.themeMode() == ThemeMode.LIGHT
              ? (jp ? Strings.Ja.SETTINGS_THEME_LIGHT : Strings.En.SETTINGS_THEME_LIGHT)
              : (jp ? Strings.Ja.SETTINGS_THEME_DARK : Strings.En.SETTINGS_THEME_DARK);
      case ITEM_RESET_SAVE ->
          jp ? Strings.Ja.SETTINGS_RESET_SAVE_VALUE : Strings.En.SETTINGS_RESET_SAVE_VALUE;
      default -> "";
    };
  }

  /**
   * キー入力を処理する。
   *
   * @return 画面遷移が発生した場合 true (呼出元は描画をスキップすること)
   */
  private boolean handleInput() {
    // 項目移動
    if (Gdx.input.isKeyJustPressed(Keys.UP) || Gdx.input.isKeyJustPressed(Keys.W)) {
      selectedItem = (selectedItem - 1 + ITEM_COUNT) % ITEM_COUNT;
    } else if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.S)) {
      selectedItem = (selectedItem + 1) % ITEM_COUNT;
    }

    // 値変更 (セーブデータ初期化の行では左右キーは何もしない)
    boolean decrease = Gdx.input.isKeyJustPressed(Keys.LEFT) || Gdx.input.isKeyJustPressed(Keys.A);
    boolean increase = Gdx.input.isKeyJustPressed(Keys.RIGHT) || Gdx.input.isKeyJustPressed(Keys.D);

    if ((decrease || increase) && selectedItem < SETTING_ITEM_COUNT) {
      current = changeValue(current, selectedItem, decrease);
      game.applySettings(current);
    }

    // セーブデータを初期化の行で ENTER → 確認ダイアログ表示
    if (selectedItem == ITEM_RESET_SAVE && Gdx.input.isKeyJustPressed(Keys.ENTER)) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      Fonts fonts = game.fonts();
      boolean jp = fonts.isJapaneseAvailable();
      resetFailed = false;
      resetConfirm =
          new ConfirmationDialog(
              fonts.large(),
              fonts.large(),
              jp ? Strings.Ja.RESET_SAVE_CONFIRM_TITLE : Strings.En.RESET_SAVE_CONFIRM_TITLE,
              jp ? Strings.Ja.RESET_SAVE_CONFIRM_BODY : Strings.En.RESET_SAVE_CONFIRM_BODY,
              jp ? Strings.Ja.RESET_SAVE_CONFIRM_HINT : Strings.En.RESET_SAVE_CONFIRM_HINT);
      return false;
    }

    // ESC で戻る (保存して即リターン: dispose後の描画を防ぐ)
    if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      game.saveSettings();
      game.changeScreen(new TitleScreen(game));
      return true; // ← この後 batch.begin() を呼ばせない
    }
    return false;
  }

  static Settings changeValue(Settings current, int item, boolean decrease) {
    return switch (item) {
      case ITEM_BGM -> {
        float delta = decrease ? -VOLUME_STEP : VOLUME_STEP;
        yield current.withBgmVolume(current.bgmVolume() + delta);
      }
      case ITEM_SE -> {
        float delta = decrease ? -VOLUME_STEP : VOLUME_STEP;
        yield current.withSeVolume(current.seVolume() + delta);
      }
      case ITEM_FULLSCREEN -> current.withFullscreen(!current.fullscreen());
      case ITEM_UI_PRESET -> {
        UiPreset[] presets = UiPreset.values();
        int idx = current.uiPreset().ordinal();
        int next =
            decrease ? (idx - 1 + presets.length) % presets.length : (idx + 1) % presets.length;
        yield current.withUiPreset(presets[next]);
      }
      // Wave 17 W17-γ / #8: テーマ (LIGHT / DARK 2 トグル、left/right どちらでも他方に反転)
      case ITEM_THEME ->
          current.withThemeMode(
              current.themeMode() == ThemeMode.LIGHT ? ThemeMode.DARK : ThemeMode.LIGHT);
      default -> current;
    };
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, true);
    if (resetConfirm != null) {
      resetConfirm.resize(width, height);
    }
  }

  @Override
  public void dispose() {
    if (batch != null) {
      batch.dispose();
    }
    if (resetConfirm != null) {
      resetConfirm.dispose();
      resetConfirm = null;
    }
  }
}
