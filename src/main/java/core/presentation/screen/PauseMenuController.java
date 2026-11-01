package core.presentation.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import core.infrastructure.audio.SeKind;
import core.infrastructure.save.Settings;
import core.presentation.render.Fonts;
import core.presentation.render.RenderLayout;
import core.presentation.render.Strings;
import core.presentation.window.ConfirmationDialog;
import core.presentation.window.TutorialOverlay;
import java.util.Objects;
import java.util.Optional;

/**
 * ダンジョン内の ESC 一時停止メニューとそのサブ画面 (設定 / 操作説明 / タイトルへ戻る確認 / セーブ初期化確認) を管理するコントローラ。
 *
 * <p>{@link EliteRewardOrchestrator} と同様に {@link DungeonScreen} から利用する部品として切り出し、 メニュー表示中の入力・描画・リソース解放を一元管理する。
 */
public final class PauseMenuController implements Disposable {

  private static final int MENU_ITEM_COUNT = 5;

  private static final int ITEM_RESUME = 0;
  private static final int ITEM_SETTINGS = 1;
  private static final int ITEM_TUTORIAL = 2;
  private static final int ITEM_RETURN_TITLE = 3;
  private static final int ITEM_RESET_SAVE = 4;

  private static final int MENU_TITLE_X = 760;
  private static final int MENU_TITLE_Y = 840;
  private static final int MENU_ITEM_X = 640;
  private static final int MENU_TOP_Y = 700;
  private static final int MENU_ROW_HEIGHT = RenderLayout.LARGE_LINE_HEIGHT + 16;
  private static final int MENU_HIT_WIDTH = 680;
  private static final int MENU_HIT_HEIGHT = 52;
  private static final int MENU_ERROR_Y = MENU_TOP_Y - MENU_ROW_HEIGHT * MENU_ITEM_COUNT - 20;
  private static final int MENU_HINT_Y = 160;

  private enum State {
    CLOSED,
    MAIN_MENU,
    SETTINGS,
    TUTORIAL,
    CONFIRM_RETURN_TITLE,
    CONFIRM_RESET_SAVE
  }

  private final DddGame game;
  private final OrthographicCamera camera;
  private final FitViewport viewport;

  private SpriteBatch batch;
  private ShapeRenderer shapes;

  private State state = State.CLOSED;
  private int selectedIndex = 0;
  private int settingsSelectedItem = 0;
  private Settings currentSettings;
  private TutorialOverlay tutorialOverlay;
  private ConfirmationDialog confirmDialog;
  private boolean resetFailed = false;

  public PauseMenuController(DddGame game) {
    this.game = Objects.requireNonNull(game, "game");
    this.camera = new OrthographicCamera();
    this.viewport = new FitViewport(RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT, camera);
    this.camera.setToOrtho(false, RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT);
    this.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
    ensureResources();
  }

  private void ensureResources() {
    if (batch == null) {
      batch = new SpriteBatch();
    }
    if (shapes == null) {
      shapes = new ShapeRenderer();
    }
  }

  /** 一時停止メニュー (またはそのサブ画面) が表示中なら true。 */
  public boolean isActive() {
    return state != State.CLOSED;
  }

  /**
   * 一時停止メニューを開く。
   *
   * <p>層末・Elite 報酬ポップアップの {@code Stage} が {@code InputProcessor} に残っていると メニュー越しのクリックが選択として処理されるため、開いた時点で
   * {@code setInputProcessor(null)} にする。
   */
  public void open() {
    ensureResources();
    closeSubViews();
    state = State.MAIN_MENU;
    selectedIndex = 0;
    resetFailed = false;
    Gdx.input.setInputProcessor(null);
    viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
  }

  /**
   * メニュー表示中の入力を 1 フレーム分処理する。
   *
   * <p>画面遷移 ({@link DddGame#changeScreen}) が発生した場合は、遷移前にサブダイアログ等を解放した上で {@code true} を返す。呼出元 ({@link
   * DungeonScreen#render}) は {@code true} を受け取ったら直ちに return し、破棄済みの {@code batch} 等に触れないこと。
   *
   * @return 画面遷移が発生した場合 true
   */
  public boolean handleInput() {
    return switch (state) {
      case CLOSED -> false;
      case MAIN_MENU -> handleMainMenuInput();
      case SETTINGS -> handleSettingsInput();
      case TUTORIAL -> handleTutorialInput();
      case CONFIRM_RETURN_TITLE -> handleConfirmReturnTitleInput();
      case CONFIRM_RESET_SAVE -> handleConfirmResetSaveInput();
    };
  }

  private boolean handleMainMenuInput() {
    if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      close();
      return false;
    }

    if (Gdx.input.isKeyJustPressed(Keys.UP) || Gdx.input.isKeyJustPressed(Keys.W)) {
      selectedIndex = (selectedIndex - 1 + MENU_ITEM_COUNT) % MENU_ITEM_COUNT;
    } else if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.S)) {
      selectedIndex = (selectedIndex + 1) % MENU_ITEM_COUNT;
    }

    for (int i = 0; i < MENU_ITEM_COUNT; i++) {
      if (isMenuNumberJustPressed(i)) {
        selectedIndex = i;
        activateMenuItem(i);
        return false;
      }
    }

    if (Gdx.input.isKeyJustPressed(Keys.ENTER) || Gdx.input.isKeyJustPressed(Keys.SPACE)) {
      activateMenuItem(selectedIndex);
      return false;
    }

    if (Gdx.input.justTouched()) {
      Vector2 world = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
      for (int i = 0; i < MENU_ITEM_COUNT; i++) {
        if (menuItemBounds(i).contains(world.x, world.y)) {
          selectedIndex = i;
          activateMenuItem(i);
          return false;
        }
      }
    }
    return false;
  }

  private void activateMenuItem(int item) {
    game.soundManager().playSe(SeKind.BUTTON_DECISION);
    Fonts fonts = game.fonts();
    boolean jp = fonts.isJapaneseAvailable();
    switch (item) {
      case ITEM_RESUME -> close();
      case ITEM_SETTINGS -> {
        settingsSelectedItem = 0;
        currentSettings = game.persistence().settings();
        state = State.SETTINGS;
      }
      case ITEM_TUTORIAL -> {
        closeSubViews();
        tutorialOverlay =
            new TutorialOverlay(
                fonts.hud(),
                jp ? Strings.Ja.TUTORIAL_TITLE : Strings.En.TUTORIAL_TITLE,
                jp ? Strings.Ja.TUTORIAL_BODY : Strings.En.TUTORIAL_BODY,
                jp ? Strings.Ja.TUTORIAL_CLOSE_HINT : Strings.En.TUTORIAL_CLOSE_HINT);
        state = State.TUTORIAL;
      }
      case ITEM_RETURN_TITLE -> {
        closeSubViews();
        confirmDialog =
            new ConfirmationDialog(
                fonts.large(),
                fonts.large(),
                jp ? Strings.Ja.RETURN_TITLE_CONFIRM_TITLE : Strings.En.RETURN_TITLE_CONFIRM_TITLE,
                jp ? Strings.Ja.RETURN_TITLE_CONFIRM_BODY : Strings.En.RETURN_TITLE_CONFIRM_BODY,
                jp ? Strings.Ja.RETURN_TITLE_CONFIRM_HINT : Strings.En.RETURN_TITLE_CONFIRM_HINT);
        state = State.CONFIRM_RETURN_TITLE;
      }
      case ITEM_RESET_SAVE -> {
        closeSubViews();
        resetFailed = false;
        confirmDialog =
            new ConfirmationDialog(
                fonts.large(),
                fonts.large(),
                jp ? Strings.Ja.RESET_SAVE_CONFIRM_TITLE : Strings.En.RESET_SAVE_CONFIRM_TITLE,
                jp ? Strings.Ja.RESET_SAVE_CONFIRM_BODY : Strings.En.RESET_SAVE_CONFIRM_BODY,
                jp ? Strings.Ja.RESET_SAVE_CONFIRM_HINT : Strings.En.RESET_SAVE_CONFIRM_HINT);
        state = State.CONFIRM_RESET_SAVE;
      }
      default -> {}
    }
  }

  private boolean handleSettingsInput() {
    if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      game.saveSettings();
      state = State.MAIN_MENU;
      return false;
    }

    int count = SettingsScreen.SETTING_ITEM_COUNT;
    if (Gdx.input.isKeyJustPressed(Keys.UP) || Gdx.input.isKeyJustPressed(Keys.W)) {
      settingsSelectedItem = (settingsSelectedItem - 1 + count) % count;
    } else if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.S)) {
      settingsSelectedItem = (settingsSelectedItem + 1) % count;
    }

    boolean decrease = Gdx.input.isKeyJustPressed(Keys.LEFT) || Gdx.input.isKeyJustPressed(Keys.A);
    boolean increase = Gdx.input.isKeyJustPressed(Keys.RIGHT) || Gdx.input.isKeyJustPressed(Keys.D);
    if (decrease || increase) {
      currentSettings = SettingsScreen.changeValue(currentSettings, settingsSelectedItem, decrease);
      game.applySettings(currentSettings);
    }
    return false;
  }

  private boolean handleTutorialInput() {
    if (Gdx.input.isKeyJustPressed(Keys.ENTER)
        || Gdx.input.isKeyJustPressed(Keys.ESCAPE)
        || Gdx.input.justTouched()) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      closeSubViews();
      state = State.MAIN_MENU;
    }
    return false;
  }

  private boolean handleConfirmReturnTitleInput() {
    if (Gdx.input.isKeyJustPressed(Keys.Y)) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      closeSubViews();
      state = State.CLOSED;
      game.suspendRunToTitle();
      game.changeScreen(new TitleScreen(game));
      return true;
    }
    if (Gdx.input.isKeyJustPressed(Keys.N) || Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      closeSubViews();
      state = State.MAIN_MENU;
    }
    return false;
  }

  private boolean handleConfirmResetSaveInput() {
    if (Gdx.input.isKeyJustPressed(Keys.Y)) {
      game.soundManager().playSe(SeKind.BUTTON_DECISION);
      closeSubViews();
      if (game.resetAllProgress()) {
        state = State.CLOSED;
        game.changeScreen(new TitleScreen(game));
        return true;
      }
      resetFailed = true;
      state = State.MAIN_MENU;
      return false;
    }
    if (Gdx.input.isKeyJustPressed(Keys.N) || Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      closeSubViews();
      state = State.MAIN_MENU;
    }
    return false;
  }

  /**
   * 現在のメニュー状態を描画する (ダンジョン画面の上に半透明の黒を重ね、その上にメニューやサブ画面を描画)。
   *
   * @param delta フレーム経過秒数
   */
  public void render(float delta) {
    if (state == State.CLOSED) {
      return;
    }
    ensureResources();
    switch (state) {
      case MAIN_MENU -> {
        drawDimOverlay(0.75f);
        drawMainMenu();
      }
      case SETTINGS -> {
        drawDimOverlay(0.82f);
        drawSettingsSubScreen();
      }
      case TUTORIAL -> {
        drawDimOverlay(0.75f);
        if (tutorialOverlay != null) {
          tutorialOverlay.render(delta);
        }
      }
      case CONFIRM_RETURN_TITLE, CONFIRM_RESET_SAVE -> {
        if (confirmDialog != null) {
          confirmDialog.render(delta);
          Optional<Boolean> extra = confirmDialog.consume();
          if (extra.isPresent() && !extra.get()) {
            closeSubViews();
            state = State.MAIN_MENU;
          }
        }
      }
      case CLOSED -> {}
    }
  }

  private void drawDimOverlay(float alpha) {
    viewport.apply();
    Gdx.gl.glEnable(GL20.GL_BLEND);
    shapes.setProjectionMatrix(camera.combined);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0f, 0f, 0f, alpha);
    shapes.rect(0f, 0f, RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT);
    shapes.end();
  }

  private void drawMainMenu() {
    Fonts fonts = game.fonts();
    boolean jp = fonts.isJapaneseAvailable();
    BitmapFont titleFont = fonts.title();
    BitmapFont large = fonts.large();

    viewport.apply();
    batch.setProjectionMatrix(camera.combined);
    batch.begin();

    titleFont.setColor(Color.WHITE);
    titleFont.draw(
        batch,
        jp ? Strings.Ja.PAUSE_TITLE : Strings.En.PAUSE_TITLE,
        MENU_TITLE_X,
        MENU_TITLE_Y);

    for (int i = 0; i < MENU_ITEM_COUNT; i++) {
      boolean selected = (i == selectedIndex);
      int y = MENU_TOP_Y - i * MENU_ROW_HEIGHT;
      large.setColor(selected ? Color.YELLOW : Color.LIGHT_GRAY);
      String prefix = selected ? "> " : "  ";
      large.draw(batch, prefix + menuItemLabel(jp, i), MENU_ITEM_X, y);
    }

    if (resetFailed) {
      large.setColor(new Color(1f, 0.35f, 0.35f, 1f));
      large.draw(
          batch,
          jp ? Strings.Ja.RESET_SAVE_FAILED_MESSAGE : Strings.En.RESET_SAVE_FAILED_MESSAGE,
          MENU_ITEM_X - 80,
          MENU_ERROR_Y);
    }

    large.setColor(Color.GRAY);
    large.draw(
        batch,
        jp ? Strings.Ja.PAUSE_HINT : Strings.En.PAUSE_HINT,
        RenderLayout.SETTINGS_LABEL_X - 80,
        MENU_HINT_Y);

    large.setColor(Color.WHITE);
    batch.end();
  }

  private void drawSettingsSubScreen() {
    Fonts fonts = game.fonts();
    boolean jp = fonts.isJapaneseAvailable();
    BitmapFont titleFont = fonts.title();
    BitmapFont large = fonts.large();

    viewport.apply();
    batch.setProjectionMatrix(camera.combined);
    batch.begin();

    titleFont.setColor(Color.WHITE);
    titleFont.draw(
        batch,
        jp ? Strings.Ja.SETTINGS_TITLE : Strings.En.SETTINGS_TITLE,
        RenderLayout.SETTINGS_TITLE_X,
        RenderLayout.SETTINGS_TITLE_Y);

    for (int i = 0; i < SettingsScreen.SETTING_ITEM_COUNT; i++) {
      boolean selected = (i == settingsSelectedItem);
      int y = RenderLayout.SETTINGS_ROW_TOP_Y - RenderLayout.SETTINGS_ROW_HEIGHT * i;
      String label = SettingsScreen.getLabel(jp, i);
      String value = SettingsScreen.getValue(currentSettings, jp, i);

      large.setColor(selected ? Color.YELLOW : Color.LIGHT_GRAY);
      String prefix = selected ? "> " : "  ";
      large.draw(batch, prefix + label, RenderLayout.SETTINGS_LABEL_X, y);

      large.setColor(selected ? Color.WHITE : Color.LIGHT_GRAY);
      large.draw(batch, value, RenderLayout.SETTINGS_VALUE_X, y);
    }

    large.setColor(Color.GRAY);
    large.draw(
        batch,
        jp ? Strings.Ja.PAUSE_SETTINGS_HINT : Strings.En.PAUSE_SETTINGS_HINT,
        RenderLayout.SETTINGS_LABEL_X,
        RenderLayout.SETTINGS_HINT_Y);

    large.setColor(Color.WHITE);
    batch.end();
  }

  private static String menuItemLabel(boolean jp, int item) {
    return switch (item) {
      case ITEM_RESUME -> jp ? Strings.Ja.PAUSE_ITEM_RESUME : Strings.En.PAUSE_ITEM_RESUME;
      case ITEM_SETTINGS -> jp ? Strings.Ja.PAUSE_ITEM_SETTINGS : Strings.En.PAUSE_ITEM_SETTINGS;
      case ITEM_TUTORIAL -> jp ? Strings.Ja.PAUSE_ITEM_TUTORIAL : Strings.En.PAUSE_ITEM_TUTORIAL;
      case ITEM_RETURN_TITLE ->
          jp ? Strings.Ja.PAUSE_ITEM_RETURN_TITLE : Strings.En.PAUSE_ITEM_RETURN_TITLE;
      case ITEM_RESET_SAVE ->
          jp ? Strings.Ja.PAUSE_ITEM_RESET_SAVE : Strings.En.PAUSE_ITEM_RESET_SAVE;
      default -> "";
    };
  }

  private static Rectangle menuItemBounds(int index) {
    float rowY = MENU_TOP_Y - index * MENU_ROW_HEIGHT;
    return new Rectangle(MENU_ITEM_X - 20f, rowY - 40f, MENU_HIT_WIDTH, MENU_HIT_HEIGHT);
  }

  private static boolean isMenuNumberJustPressed(int index) {
    return switch (index) {
      case 0 -> Gdx.input.isKeyJustPressed(Keys.NUM_1) || Gdx.input.isKeyJustPressed(Keys.NUMPAD_1);
      case 1 -> Gdx.input.isKeyJustPressed(Keys.NUM_2) || Gdx.input.isKeyJustPressed(Keys.NUMPAD_2);
      case 2 -> Gdx.input.isKeyJustPressed(Keys.NUM_3) || Gdx.input.isKeyJustPressed(Keys.NUMPAD_3);
      case 3 -> Gdx.input.isKeyJustPressed(Keys.NUM_4) || Gdx.input.isKeyJustPressed(Keys.NUMPAD_4);
      case 4 -> Gdx.input.isKeyJustPressed(Keys.NUM_5) || Gdx.input.isKeyJustPressed(Keys.NUMPAD_5);
      default -> false;
    };
  }

  /** メニューを閉じてサブビューを解放する (Batch / ShapeRenderer は維持)。 */
  public void close() {
    closeSubViews();
    state = State.CLOSED;
    resetFailed = false;
  }

  private void closeSubViews() {
    if (tutorialOverlay != null) {
      tutorialOverlay.dispose();
      tutorialOverlay = null;
    }
    if (confirmDialog != null) {
      confirmDialog.dispose();
      confirmDialog = null;
    }
  }

  public void resize(int width, int height) {
    viewport.update(width, height, true);
    if (tutorialOverlay != null) {
      tutorialOverlay.resize(width, height);
    }
    if (confirmDialog != null) {
      confirmDialog.resize(width, height);
    }
  }

  @Override
  public void dispose() {
    closeSubViews();
    state = State.CLOSED;
    resetFailed = false;
    if (batch != null) {
      batch.dispose();
      batch = null;
    }
    if (shapes != null) {
      shapes.dispose();
      shapes = null;
    }
  }
}
