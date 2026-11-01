package core.infrastructure.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 層単位セーブの永続化を担当する (§15-11)。
 *
 * <p>保存先: {@code <user.home>/.ddd-jyogi-kuroto-f/save.json}。スロットは 1 つ。
 *
 * <p>破損 JSON・欠損ファイル・I/O エラーは「セーブなし」として扱い、呼出元をクラッシュさせない (graceful)。 エラーは {@link Logger} で
 * WARN/SEVERE レベルに記録する。
 */
public final class SaveManager {

  private static final Logger LOG = Logger.getLogger(SaveManager.class.getName());

  /** 全セーブ系ファイルを格納するユーザーホーム配下のディレクトリ名。SettingsManager もこの定数を参照して書込先を一致させる。 */
  public static final String SAVE_DIR_NAME = ".ddd-jyogi-kuroto-f";

  private static final String SAVE_FILE_NAME = "save.json";

  /** 恒久状態の保存先ファイル名 (SAVE-01)。 */
  static final String PROFILE_FILE_NAME = "profile.json";

  /** 進行中ランの保存先ファイル名 (SAVE-01)。 */
  static final String CHECKPOINT_FILE_NAME = "run-checkpoint.json";

  /** 旧セーブ移行の journal ファイル名 (SAVE-02B)。 */
  static final String MIGRATION_STATE_FILE_NAME = "migration-state.json";

  private final File saveFile;
  private final ObjectMapper mapper;

  /**
   * 保存操作の結果 (SAVE-01)。
   *
   * <p>従来の {@code save(SaveData)} は I/O 例外をログに落として戻り値なしで返していたため、 呼出側が失敗を検知できなかった。Profile
   * の保存失敗は進捗消失に直結するので、成功と失敗を 明示的に返す。
   *
   * @param success 保存に成功したか
   * @param message 失敗理由 (成功時は空文字)
   */
  public record SaveResult(boolean success, String message) {

    public static SaveResult ok() {
      return new SaveResult(true, "");
    }

    public static SaveResult failure(String message) {
      return new SaveResult(false, message);
    }

    /** 保存に成功したか。 */
    public boolean isSuccess() {
      return success;
    }
  }

  /** デフォルトコンストラクタ。保存先は {@code <user.home>/.ddd-jyogi-kuroto-f/save.json}。 */
  public SaveManager() {
    this(
        new File(System.getProperty("user.home"), SAVE_DIR_NAME + File.separator + SAVE_FILE_NAME));
  }

  /**
   * テスト用コンストラクタ。任意の保存先を指定できる。
   *
   * @param saveFile セーブファイルの絶対パス
   */
  SaveManager(File saveFile) {
    this.saveFile = saveFile;
    this.mapper = new ObjectMapper();
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * セーブデータをファイルに書き込む。
   *
   * <p>保存先ディレクトリが存在しない場合は自動生成する。I/O エラー時は SEVERE ログを出力して失敗を無視する (セーブ失敗は警告だが即クラッシュしない設計)。
   *
   * @param data 保存するセーブデータ
   */
  public void save(SaveData data) {
    try {
      File dir = saveFile.getParentFile();
      if (dir != null && !dir.exists()) {
        boolean created = dir.mkdirs();
        if (!created && !dir.exists()) {
          LOG.severe("Failed to create save directory: " + dir.getAbsolutePath());
          return;
        }
      }
      mapper.writeValue(saveFile, data);
      LOG.info("Saved to " + saveFile.getAbsolutePath());
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to save: " + saveFile.getAbsolutePath(), e);
    }
  }

  /**
   * セーブデータをファイルから読み込む。
   *
   * <p>ファイルが存在しない場合・破損 JSON・欠損フィールドは {@link Optional#empty()} を返す (graceful)。
   *
   * @return 読み込んだセーブデータ。ファイルなし / 破損時は empty
   */
  public Optional<SaveData> load() {
    if (!saveFile.exists()) {
      return Optional.empty();
    }
    try {
      SaveData data = mapper.readValue(saveFile, SaveData.class);
      return Optional.of(data);
    } catch (IOException e) {
      LOG.log(
          Level.WARNING, "Failed to load (treating as no save): " + saveFile.getAbsolutePath(), e);
      return Optional.empty();
    } catch (RuntimeException e) {
      // SaveData compact constructor のバリデーション違反 (IllegalArgumentException) のほか、
      // List/Map.copyOf が null 要素で NullPointerException を投げるケース (ユーザー編集の
      // 不正 JSON / Schema migration 過程の null 要素) も graceful にロードなし扱いにする。
      LOG.log(
          Level.WARNING,
          "Invalid save data (treating as no save): " + saveFile.getAbsolutePath(),
          e);
      return Optional.empty();
    }
  }

  /**
   * セーブファイルが存在するかを返す。
   *
   * @return ファイルが存在すれば true
   */
  public boolean exists() {
    return saveFile.exists() && saveFile.isFile();
  }

  /**
   * セーブファイルを削除する。
   *
   * <p>存在しない場合は何もしない。削除失敗は WARN ログを出力するが例外は投げない。
   */
  public void delete() {
    if (!saveFile.exists()) {
      return;
    }
    boolean deleted = saveFile.delete();
    if (!deleted) {
      LOG.warning("Failed to delete save file: " + saveFile.getAbsolutePath());
    } else {
      LOG.info("Deleted save file: " + saveFile.getAbsolutePath());
    }
  }

  // -------------------------------------------------------------------------
  // SAVE-01: Profile と RunCheckpoint の分離保存
  // -------------------------------------------------------------------------

  /** 恒久状態を保存する。メタ進行の変更時とラン終了時に呼ぶ。 */
  public SaveResult saveProfile(ProfileData profile) {
    if (isProfileUnsafeToOverwrite()) {
      String message =
          "Refusing to overwrite file with future schemaVersion, corrupted content, or invalid data (unsafe to overwrite): "
              + profileFile().getAbsolutePath();
      LOG.severe(message);
      return SaveResult.failure(message);
    }
    return writeAtomically(profileFile(), profile);
  }

  /** 進行中ランを保存する。層と層の間だけで呼ぶ。 */
  public SaveResult saveCheckpoint(RunCheckpoint checkpoint) {
    if (isCheckpointUnsafeToOverwrite()) {
      String message =
          "Refusing to overwrite file with future schemaVersion, corrupted content, or invalid data (unsafe to overwrite): "
              + checkpointFile().getAbsolutePath();
      LOG.severe(message);
      return SaveResult.failure(message);
    }
    return writeAtomically(checkpointFile(), checkpoint);
  }

  public boolean isProfileUnsafeToOverwrite() {
    return isUnsafeToOverwrite(
        profileFile(), ProfileData.class, ProfileData.CURRENT_SCHEMA_VERSION);
  }

  public boolean isCheckpointUnsafeToOverwrite() {
    return isUnsafeToOverwrite(
        checkpointFile(), RunCheckpoint.class, RunCheckpoint.CURRENT_SCHEMA_VERSION);
  }

  private boolean isUnsafeToOverwrite(File file, Class<?> type, int supportedVersion) {
    if (!file.isFile()) {
      return false;
    }
    try {
      JsonNode node = mapper.readTree(file);
      int version = node.path("schemaVersion").asInt(0);
      if (version > supportedVersion) {
        return true;
      }
      mapper.treeToValue(node, type);
      return false;
    } catch (IOException | RuntimeException e) {
      return true;
    }
  }

  /** 恒久状態を読み込む。欠損・破損・未来 schema は empty。 */
  public Optional<ProfileData> loadProfile() {
    return read(profileFile(), ProfileData.class, ProfileData.CURRENT_SCHEMA_VERSION);
  }

  /** 進行中ランを読み込む。欠損・破損・未来 schema は empty。 */
  public Optional<RunCheckpoint> loadCheckpoint() {
    return read(checkpointFile(), RunCheckpoint.class, RunCheckpoint.CURRENT_SCHEMA_VERSION);
  }

  public boolean profileExists() {
    return profileFile().isFile();
  }

  public boolean checkpointExists() {
    return checkpointFile().isFile();
  }

  /** 恒久状態を削除する。通常は移行と初期化以外で呼ばない。 */
  public void deleteProfile() {
    if (isProfileUnsafeToOverwrite()) {
      LOG.warning(
          "Refusing to delete profile file with future schemaVersion, corrupted content, or invalid data: "
              + profileFile().getAbsolutePath());
      return;
    }
    deleteQuietly(profileFile());
  }

  /** 進行中ランを削除する。死亡・クリアの精算後に呼ぶ。 */
  public void deleteCheckpoint() {
    if (isCheckpointUnsafeToOverwrite()) {
      LOG.warning(
          "Refusing to delete checkpoint file with future schemaVersion, corrupted content, or invalid data: "
              + checkpointFile().getAbsolutePath());
      return;
    }
    deleteQuietly(checkpointFile());
  }

  /**
   * 来場者交代用に進行データ一式を削除する ({@code settings.json} は残す)。
   *
   * <p>明示的な初期化操作のため、破損ファイルや未来 {@code schemaVersion} のファイルも削除する。
   *
   * @return 全対象ファイルが存在しない状態になれば {@link SaveResult#ok()}、1 件でも残れば {@link SaveResult#failure(String)}
   */
  public SaveResult deleteAllProgress() {
    File dir = saveFile.getParentFile();
    java.util.Set<File> targets = new java.util.LinkedHashSet<>();
    targets.add(profileFile());
    targets.add(checkpointFile());
    targets.add(saveFile);
    targets.add(new File(dir, SAVE_FILE_NAME));
    targets.add(new File(dir, LegacySaveMigrator.LEGACY_BACKUP_FILE_NAME));
    targets.add(migrationStateFile());
    targets.add(new File(dir, PROFILE_FILE_NAME + ".bak"));
    targets.add(new File(dir, CHECKPOINT_FILE_NAME + ".bak"));
    targets.add(new File(dir, SAVE_FILE_NAME + ".bak"));
    targets.add(new File(dir, saveFile.getName() + ".bak"));
    targets.add(new File(dir, MIGRATION_STATE_FILE_NAME + ".bak"));

    java.util.List<String> remaining = new java.util.ArrayList<>();
    for (File target : targets) {
      if (target.exists()) {
        boolean deleted = target.delete();
        if (!deleted || target.exists()) {
          LOG.severe("Failed to delete progress file: " + target.getAbsolutePath());
          remaining.add(target.getName());
        } else {
          LOG.info("Deleted progress file: " + target.getAbsolutePath());
        }
      }
    }
    if (!remaining.isEmpty()) {
      return SaveResult.failure("Failed to delete progress files: " + String.join(", ", remaining));
    }
    return SaveResult.ok();
  }

  /** 旧 {@code save.json} のパス。移行 (SAVE-02B) がハッシュ計算に使う。 */
  File legacySaveFile() {
    return saveFile;
  }

  /** 移行 journal を保存する (SAVE-02B)。 */
  SaveResult saveMigrationState(LegacyMigrationState state) {
    return writeAtomically(migrationStateFile(), state);
  }

  /** 移行 journal を読み込む (SAVE-02B)。 */
  Optional<LegacyMigrationState> loadMigrationState() {
    // journal は schemaVersion を持たないため版チェックは不要。
    File file = migrationStateFile();
    if (!file.isFile()) {
      return Optional.empty();
    }
    try {
      return Optional.of(mapper.readValue(file, LegacyMigrationState.class));
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Invalid migration journal (treating as absent)", e);
      return Optional.empty();
    }
  }

  File migrationStateFile() {
    return new File(saveFile.getParentFile(), MIGRATION_STATE_FILE_NAME);
  }

  File profileFile() {
    return new File(saveFile.getParentFile(), PROFILE_FILE_NAME);
  }

  File checkpointFile() {
    return new File(saveFile.getParentFile(), CHECKPOINT_FILE_NAME);
  }

  /**
   * 同じディレクトリの一時ファイルへ書いてから原子的に置換する。
   *
   * <p>原子的置換が使えない環境では、旧世代を {@code .bak} へ退避してから置換し、読み直して 検証できた場合だけ {@code .bak} を消す。途中で失敗したら {@code
   * .bak} を戻し、成功を返さない。 書込中に電源が落ちても、旧世代か新世代のどちらかが残る。
   */
  private SaveResult writeAtomically(File target, Object value) {
    File dir = target.getParentFile();
    if (dir != null && !dir.exists() && !dir.mkdirs() && !dir.exists()) {
      String message = "Failed to create save directory: " + dir.getAbsolutePath();
      LOG.severe(message);
      return SaveResult.failure(message);
    }

    // 期待するバイト列を先に作る。置換後に読み直して突き合わせる材料になる。
    byte[] payload;
    try {
      payload = mapper.writeValueAsBytes(value);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to serialize: " + target.getAbsolutePath(), e);
      return SaveResult.failure("serialize failed: " + e.getMessage());
    }

    // 一時ファイル名は一意にする。固定名だと 2 プロセスが同時に書いたとき互いの
    // 中間状態を上書きし、壊れた JSON がそのまま正本になる。
    File temp;
    try {
      temp = Files.createTempFile(dir.toPath(), target.getName(), ".tmp").toFile();
      Files.write(temp.toPath(), payload);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to write temp file for: " + target.getAbsolutePath(), e);
      return SaveResult.failure("write failed: " + e.getMessage());
    }

    try {
      Files.move(
          temp.toPath(),
          target.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      LOG.info("Saved to " + target.getAbsolutePath());
      return SaveResult.ok();
    } catch (AtomicMoveNotSupportedException e) {
      return replaceWithBackup(temp, target, payload);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to replace: " + target.getAbsolutePath(), e);
      deleteQuietly(temp);
      return SaveResult.failure("replace failed: " + e.getMessage());
    }
  }

  /**
   * 原子的置換が使えない環境向けのフォールバック。旧世代を {@code .bak} で守る。
   *
   * <p>置換後に <b>実際に読み直して</b> 期待バイト列と一致することを確かめ、一致した場合だけ {@code .bak} を消す。{@code Files.move}
   * の戻り値だけを根拠に旧世代を捨てると、非原子コピーが 途中で切れた場合に唯一の正本が壊れたファイルになる。
   */
  private SaveResult replaceWithBackup(File temp, File target, byte[] payload) {
    File backup = new File(target.getParentFile(), target.getName() + ".bak");
    boolean hadPrevious = target.isFile();
    if (hadPrevious && !target.renameTo(backup)) {
      deleteQuietly(temp);
      String message = "Failed to back up previous save: " + target.getAbsolutePath();
      LOG.severe(message);
      return SaveResult.failure(message);
    }
    try {
      Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
      if (!Arrays.equals(Files.readAllBytes(target.toPath()), payload)) {
        throw new IOException("verification failed: written bytes differ from payload");
      }
      LOG.info("Saved to " + target.getAbsolutePath() + " (non-atomic fallback, verified)");
      deleteQuietly(backup);
      return SaveResult.ok();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Fallback replace failed: " + target.getAbsolutePath(), e);
      deleteQuietly(temp);
      restoreBackup(backup, target, hadPrevious);
      return SaveResult.failure("fallback replace failed: " + e.getMessage());
    }
  }

  /** 検証に失敗したときに旧世代を書き戻す。 */
  private static void restoreBackup(File backup, File target, boolean hadPrevious) {
    if (!hadPrevious || !backup.isFile()) {
      return;
    }
    deleteQuietly(target); // 壊れた新世代を先に除く (renameTo は既存ファイルへ失敗しうる)
    if (!backup.renameTo(target)) {
      LOG.severe("Failed to restore backup: " + backup.getAbsolutePath());
    }
  }

  /**
   * JSON を読み込む。未来の {@code schemaVersion} は読込を拒否し、ファイルには一切触れない。
   *
   * <p>拒否したファイルを消したり上書きしたりしないのは、新しい版のゲームで作られたセーブを 古い版で起動しただけで壊さないため。
   */
  private <T> Optional<T> read(File file, Class<T> type, int supportedVersion) {
    if (!file.isFile()) {
      return Optional.empty();
    }
    try {
      JsonNode node = mapper.readTree(file);
      int version = node.path("schemaVersion").asInt(0);
      if (version > supportedVersion) {
        LOG.warning(
            "Refusing to load future schemaVersion "
                + version
                + " (supported: "
                + supportedVersion
                + "): "
                + file.getAbsolutePath());
        return Optional.empty();
      }
      return Optional.of(mapper.treeToValue(node, type));
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to load (treating as absent): " + file.getAbsolutePath(), e);
      return Optional.empty();
    } catch (RuntimeException e) {
      // compact constructor のバリデーション違反 (保護枠超過など) も graceful に「なし」扱いにする。
      LOG.log(Level.WARNING, "Invalid data (treating as absent): " + file.getAbsolutePath(), e);
      return Optional.empty();
    }
  }

  private static void deleteQuietly(File file) {
    if (file.exists() && !file.delete()) {
      LOG.warning("Failed to delete: " + file.getAbsolutePath());
    }
  }
}
