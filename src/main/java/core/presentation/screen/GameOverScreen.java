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
import core.infrastructure.audio.BgmKind;
import core.infrastructure.audio.SeKind;
import core.presentation.render.Fonts;
import core.presentation.render.RenderLayout;
import core.presentation.render.Strings;

/**
 * ラン終了画面 (死亡 or クリア)。ENTER で新しいラン (タイトルに戻る) に遷移する。
 *
 * <p>GAME_DESIGN §5-3 の死亡時挙動に従い「ソウル・スキル・スキル枠は保持」だが、MVP では新ランで 完全初期化する (ソウル使用 UI
 * が未実装のため、保持しても活かす場面が無い)。これは MVP の YAGNI 判断。
 */
public final class GameOverScreen extends ScreenAdapter {

  private final DddGame game;
  private final boolean cleared;

  private int soulSnapshot;

  /**
   * ラン終了処理の結果。保存に失敗したランからソウルツリーへ進ませないために保持する。
   *
   * <p>{@code onRunEnded()} が void だった頃は成否を見ずに画面を成立させていたため、保存失敗後でも ENTER
   * でソウルツリーへ進めた。進んだ先で解放してもディスクには残らない。
   */
  private DddGame.RunEndResult runEndResult = DddGame.RunEndResult.SETTLED;

  private OrthographicCamera camera;
  private Viewport viewport;
  private SpriteBatch batch;

  public GameOverScreen(DddGame game, boolean cleared) {
    this.game = game;
    this.cleared = cleared;
  }

  @Override
  public void show() {
    // §15-7 / E-2: ラン終了処理。Player.soul をラン外保持 (DddGame.playerSoul) に書き戻し、
    // 周回数を +1 する。これにより獲得ソウルがソウルツリーで使えるようになる。
    runEndResult = game.onRunEnded();
    soulSnapshot = game.progress().playerSoul().amount();
    // §15-5: ゲームオーバー / クリア時にタイトル BGM へ切替 (ダンジョン BGM を停止して引継)
    game.soundManager().playBgm(BgmKind.TITLE);
    camera = new OrthographicCamera();
    viewport = new FitViewport(RenderLayout.SCREEN_WIDTH, RenderLayout.SCREEN_HEIGHT, camera);
    batch = new SpriteBatch();
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(0.02f, 0.02f, 0.03f, 1f);
    viewport.apply();
    batch.setProjectionMatrix(camera.combined);

    Fonts fonts = game.fonts();
    boolean jp = fonts.isJapaneseAvailable();
    BitmapFont title = fonts.title();
    BitmapFont large = fonts.large();
    BitmapFont hud = fonts.hud();

    batch.begin();
    if (cleared) {
      title.setColor(0.9f, 0.85f, 0.4f, 1f);
      title.draw(
          batch,
          jp ? Strings.Ja.RUN_CLEARED_HEADER : Strings.En.RUN_CLEARED_HEADER,
          RenderLayout.GAMEOVER_HEADER_X,
          RenderLayout.GAMEOVER_HEADER_Y);
    } else {
      title.setColor(0.9f, 0.3f, 0.3f, 1f);
      title.draw(
          batch,
          jp ? Strings.Ja.GAME_OVER_HEADER : Strings.En.GAME_OVER_HEADER,
          RenderLayout.GAMEOVER_HEADER_X,
          RenderLayout.GAMEOVER_HEADER_Y);
    }

    large.setColor(Color.LIGHT_GRAY);
    hud.setColor(Color.GRAY);
    switch (runEndResult) {
      case SETTLED, ALREADY_SETTLED -> {
        String soulLabel = jp ? Strings.Ja.SOULS_KEPT : Strings.En.SOULS_KEPT;
        large.draw(
            batch,
            soulLabel + soulSnapshot,
            RenderLayout.GAMEOVER_SOUL_X,
            RenderLayout.GAMEOVER_SOUL_Y);
        hud.draw(
            batch,
            jp ? Strings.Ja.NEW_RUN_HINT : Strings.En.NEW_RUN_HINT,
            RenderLayout.GAMEOVER_HINT_X,
            RenderLayout.GAMEOVER_HINT_Y);
      }
      case SAVE_FAILED -> {
        large.draw(
            batch,
            jp ? Strings.Ja.GAMEOVER_SAVE_FAILED_MESSAGE : Strings.En.GAMEOVER_SAVE_FAILED_MESSAGE,
            RenderLayout.GAMEOVER_SOUL_X,
            RenderLayout.GAMEOVER_SOUL_Y);
        large.draw(
            batch,
            jp ? Strings.Ja.GAMEOVER_SAVE_FAILED_DETAIL : Strings.En.GAMEOVER_SAVE_FAILED_DETAIL,
            RenderLayout.GAMEOVER_SOUL_X,
            RenderLayout.GAMEOVER_SOUL_Y - RenderLayout.LARGE_LINE_HEIGHT);
        hud.draw(
            batch,
            jp ? Strings.Ja.GAMEOVER_SAVE_FAILED_HINT : Strings.En.GAMEOVER_SAVE_FAILED_HINT,
            RenderLayout.GAMEOVER_HINT_X,
            RenderLayout.GAMEOVER_HINT_Y);
      }
      case INVALID_STATE -> {
        large.draw(
            batch,
            jp
                ? Strings.Ja.GAMEOVER_INVALID_STATE_MESSAGE
                : Strings.En.GAMEOVER_INVALID_STATE_MESSAGE,
            RenderLayout.GAMEOVER_SOUL_X,
            RenderLayout.GAMEOVER_SOUL_Y);
        hud.draw(
            batch,
            jp ? Strings.Ja.GAMEOVER_INVALID_STATE_HINT : Strings.En.GAMEOVER_INVALID_STATE_HINT,
            RenderLayout.GAMEOVER_HINT_X,
            RenderLayout.GAMEOVER_HINT_Y);
      }
    }
    batch.end();

    if ((runEndResult == DddGame.RunEndResult.SAVE_FAILED
            || runEndResult == DddGame.RunEndResult.INVALID_STATE)
        && Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
      Gdx.app.exit();
      return;
    }

    // Wave 14 W14-β: ENTER または左クリックでソウルツリーへ遷移 (マウスのみで操作可能)
    if (Gdx.input.isKeyJustPressed(Keys.ENTER) || Gdx.input.justTouched()) {
      switch (runEndResult) {
        case SETTLED, ALREADY_SETTLED -> {
          // §15-7 / E-2: ラン終了後はソウルツリーへ遷移し、獲得ソウルを永続強化に使う。
          // startNewRun() はここでは呼ばない (次ラン開始は TitleScreen の ENTER 時)。
          game.soundManager().playSe(SeKind.BUTTON_DECISION);
          game.changeScreen(new SoulTreeScreen(game));
        }
        case SAVE_FAILED -> {
          // 保存に失敗したランからは進ませない。RunSession は保持されているので再試行できる。
          runEndResult = game.onRunEnded();
          soulSnapshot = game.progress().playerSoul().amount();
        }
        case INVALID_STATE -> {
          // Checkpoint / Soul が不正。再試行しても直らないので進ませない。
        }
      }
    }
  }

  @Override
  public void resize(int width, int height) {
    // true でカメラ位置をリセット (FitViewport の黒帯を正しく配置)
    viewport.update(width, height, true);
  }

  @Override
  public void dispose() {
    if (batch != null) {
      batch.dispose();
    }
  }
}
