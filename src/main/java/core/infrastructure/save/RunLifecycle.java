package core.infrastructure.save;

import core.application.RunId;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * ランの開始・層境界セーブ・再開・放棄の判定を集約する (SAVE-03B)。
 *
 * <p>レビュー P0-1 の是正をゲームへ接続する層。判定規則を {@code DddGame} から切り出すのは、 {@code Game} を継承した画面クラスは LibGDX
 * 無しでは動かせず、契約試験が書けないため。 本クラスは LibGDX に依存せず、{@link SaveManager} のファイル操作だけを使う。
 *
 * <p>再開の可否は「Checkpoint が存在し、その {@code runId} が Profile の {@code activeRunId} と 一致する」ことを条件にする。ラン終了時に
 * {@code activeRunId} を解除すれば、Checkpoint が 消し損ねて残っていても再開されない。
 *
 * <p>書込順序は <b>Checkpoint → Profile</b> に固定する。逆順にすると、Profile だけ先に activeRunId を 指した状態で落ちたとき、存在しない
 * Checkpoint を指す Profile が残る。Checkpoint を先に書けば、 途中で落ちても両者が食い違うだけで「再開不可」に倒れ、データは壊れない。
 */
public final class RunLifecycle {

  private static final Logger LOG = Logger.getLogger(RunLifecycle.class.getName());

  private final SaveManager saveManager;

  public RunLifecycle(SaveManager saveManager) {
    this.saveManager = Objects.requireNonNull(saveManager, "saveManager");
  }

  /** 新規ラン開始の可否。 */
  public enum StartDecision {
    /** そのまま開始してよい。 */
    ALLOWED,
    /** 進行中ランがある。続行か放棄を選ばせる必要がある。 */
    REQUIRES_RESOLUTION
  }

  /**
   * 再開できる Checkpoint を返す。
   *
   * <p>Profile が無い、Checkpoint が無い、両者の ID が食い違う場合は empty。 終了済みランの Checkpoint が残っていても、{@code
   * activeRunId} が解除されていれば再開しない。
   */
  public Optional<RunCheckpoint> resumableCheckpoint() {
    Optional<ProfileData> profile = saveManager.loadProfile();
    if (profile.isEmpty()) {
      return Optional.empty();
    }
    String activeRunId = profile.get().activeRunId();
    if (activeRunId == null) {
      return Optional.empty();
    }
    Optional<RunCheckpoint> checkpoint = saveManager.loadCheckpoint();
    if (checkpoint.isEmpty() || !activeRunId.equals(checkpoint.get().runId())) {
      return Optional.empty();
    }
    return checkpoint;
  }

  /** タイトルで「つづき」を出してよいか。Profile だけが存在する場合は出さない。 */
  public boolean canContinue() {
    return resumableCheckpoint().isPresent();
  }

  /** 新規ランを始めてよいか。進行中ランがあるなら続行か放棄を選ばせる。 */
  public StartDecision decideNewRun() {
    return canContinue() ? StartDecision.REQUIRES_RESOLUTION : StartDecision.ALLOWED;
  }

  /**
   * ラン開始を記録する。Profile の {@code activeRunId} を新しいランへ向け、古い Checkpoint を消す。
   *
   * <p>Checkpoint の削除を先に行うのは、消し損ねた Checkpoint が新しい activeRunId とたまたま 一致することを避けるため (ID は UUID
   * なので実際上は起きないが、順序で保証しておく)。
   */
  public SaveManager.SaveResult beginRun(ProfileData profile, RunId runId) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(runId, "runId");
    if (saveManager.isProfileUnsafeToOverwrite() || saveManager.isCheckpointUnsafeToOverwrite()) {
      String message =
          "Refusing to begin run: profile or checkpoint file is unsafe to overwrite/delete";
      LOG.severe(message);
      return SaveManager.SaveResult.failure(message);
    }
    SaveManager.SaveResult saveResult =
        saveManager.saveProfile(profile.withActiveRunId(runId.value()));
    if (!saveResult.isSuccess()) {
      return saveResult;
    }
    saveManager.deleteCheckpoint();
    return SaveManager.SaveResult.ok();
  }

  /**
   * 層境界で進行中ランを保存する。
   *
   * <p>Checkpoint を書いてから Profile の activeRunId を確定する。途中で落ちた場合は ID が食い違って再開不可になるだけで、恒久進捗は壊れない。
   */
  public SaveManager.SaveResult saveAtLayerBoundary(ProfileData profile, RunCheckpoint checkpoint) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(checkpoint, "checkpoint");
    SaveManager.SaveResult checkpointResult = saveManager.saveCheckpoint(checkpoint);
    if (!checkpointResult.isSuccess()) {
      LOG.severe("Layer boundary save aborted: checkpoint write failed");
      return checkpointResult;
    }
    return saveManager.saveProfile(profile.withActiveRunId(checkpoint.runId()));
  }

  /**
   * ラン終了を記録する。恒久進捗を保存してから Checkpoint を削除する。
   *
   * <p>順序が逆だと、Checkpoint を消した直後に Profile 保存が失敗した場合にラン成果が消える。 Profile を先に確定すれば、Checkpoint が残っても
   * activeRunId が解除済みなので再開されない。
   *
   * @param settledProfile 精算済みの恒久進捗 ({@code withSettledRunId} 済みであること)
   */
  public SaveManager.SaveResult endRun(ProfileData settledProfile) {
    Objects.requireNonNull(settledProfile, "settledProfile");
    if (settledProfile.activeRunId() != null) {
      throw new IllegalArgumentException(
          "settledProfile must have no activeRunId: " + settledProfile.activeRunId());
    }
    SaveManager.SaveResult result = saveManager.saveProfile(settledProfile);
    if (!result.isSuccess()) {
      LOG.severe("Run end save failed; keeping checkpoint so the run is not silently lost");
      return result;
    }
    saveManager.deleteCheckpoint();
    return result;
  }

  /** 現在の Profile。存在しない場合は新規プレイヤーの初期状態。 */
  public ProfileData profileOrInitial() {
    return saveManager.loadProfile().orElseGet(ProfileData::initial);
  }

  /** 来場者交代用に進行データ一式を削除する ({@code settings.json} は残す)。 */
  public SaveManager.SaveResult deleteAllProgress() {
    return saveManager.deleteAllProgress();
  }
}
