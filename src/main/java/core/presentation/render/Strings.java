package core.presentation.render;

/**
 * 画面テキストの文言定数。日本語と英語の 2 セットを持つ。
 *
 * <p>{@link Fonts#isJapaneseAvailable()} の真偽でどちらのセットを使うかを呼び出し側が判断する。 国際化ライブラリ (gdx-i18n /
 * .properties) は MVP では過剰なので、定数クラスで足りる範囲にする (YAGNI)。文言が増えてきたら {@link
 * com.badlogic.gdx.utils.I18NBundle} に切り替える。
 */
public final class Strings {

  private Strings() {}

  /** 日本語版。Noto Sans JP が assets/fonts に置かれているときに使う。 */
  public static final class Ja {
    private Ja() {}

    public static final String TITLE = "Deep Dead Dungeons";
    // PLATFORM-01: 「どこでも動く」は Android 未実装のため未実証の訴求だった。作品の中身を表す語へ差し替える。
    public static final String SUBTITLE = "- 死を重ねて挑む戦術ローグライト -";
    public static final String START_HINT = "ENTER で出発する";
    public static final String CONTROLS_HEADER = "操作";
    public static final String CONTROLS_MOVE = "WASD / 矢印キー   1 マス移動 (AP 1)";
    public static final String CONTROLS_SKILL = "1 〜 9         カード選択 → 方向キーで使用";
    public static final String CONTROLS_WAIT = "SPACE          待機 (AP 1)";
    public static final String CONTROLS_END = "ENTER          ターン終了";

    public static final String HUD_HP = "HP";
    public static final String HUD_AP = "AP";
    public static final String HUD_SOUL = "ソウル";
    public static final String HUD_PHASE = "フェーズ";
    public static final String HUD_SPEED = "速度";
    public static final String HUD_HINT = "Tab: ステータス   ENTER: ターン終了";

    // 移動権保持中の操作ヒント (ADR-21 §15-5)
    public static final String MOVE_TOKEN_HINT = "移動権保持中: WASD/矢印で移動   ENTER で終了";

    public static final String PHASE_PLAYER = "あなたのターン";
    public static final String PHASE_ENEMY = "敵のターン";
    public static final String PHASE_GAMEOVER = "ゲームオーバー";
    public static final String PHASE_CLEARED = "踏破成功";
    public static final String PHASE_RUN_CLEARED = "ダンジョン制覇";

    public static final String GAME_OVER_HEADER = "敗 北";
    public static final String RUN_CLEARED_HEADER = "ダンジョン制覇";
    public static final String SOULS_KEPT = "持ち帰ったソウル: ";
    public static final String NEW_RUN_HINT = "ENTER でソウルツリーへ";
    public static final String GAMEOVER_SAVE_FAILED_MESSAGE = "保存に失敗しました。まだ記録されていません";
    public static final String GAMEOVER_SAVE_FAILED_DETAIL = "空き容量や書き込み権限を確認してください";
    public static final String GAMEOVER_SAVE_FAILED_HINT = "ENTER: 再試行   ESC: 保存せずに終了";
    public static final String GAMEOVER_INVALID_STATE_MESSAGE =
        "データの整合性エラーのため精算を中止しました。保存データは変更していません";
    public static final String GAMEOVER_INVALID_STATE_HINT = "ESC: 終了";

    // CLEARED 状態 (階段踏破直後) の操作ヒント (§15-6 / §15-8 / E-6)。
    // ポップアップが前面に出るが、HUD ヒント領域にも同等情報を残して二重視認性を確保。
    public static final String CLEARED_HINT = "層末ノード選択中: 1〜3 で選択";

    // 層末ノード選択ポップアップのタイトル (§15-8 / E-6)
    public static final String LAYER_END_TITLE = "層末ノード — 1 つ選んでください";

    // 手札表示ラベル
    public static final String HAND_HINT = "数字キー/クリック: 選択  WASD/矢印/マップクリック: 方向  ESC: キャンセル";
    public static final String CARD_ELEMENT_PHYSICAL = "物";
    public static final String CARD_ELEMENT_MAGICAL = "魔";

    // 移動権ラベル (ADR-21 §15-5: 移動カードで付与された無料移動権の残量表示)
    public static final String MOVE_TOKEN_REMAINING_FORMAT = "移動権 残 %d 歩";

    // BattleEvent の説明文 (HUD ログ用)
    public static final String EV_MOVED_FORMAT = "%s が (%d, %d) へ移動";
    public static final String EV_SKILL_USED_FORMAT = "%s が「%s」を発動";
    public static final String EV_DAMAGE_FORMAT = "%s → %s に %d ダメージ (残 HP %d)";
    public static final String EV_DIED_FORMAT = "%s 撃破";
    public static final String EV_SOUL_GAINED_FORMAT = "%s は %d ソウルを獲得";
    public static final String EV_PHASE_FORMAT = "フェーズ移行 → %s";
    public static final String EV_REJECTED_FORMAT = "%s: 拒否 (%s)";
    public static final String EV_MOVEMENT_GRANTED_FORMAT = "%s に移動権 %d 歩を付与";
    public static final String EV_TRAP_PLACED_FORMAT = "%s が (%d, %d) に罠を設置";
    public static final String EV_TRAP_TRIGGERED_FORMAT = "%s が罠を踏み %d ダメージ (残 HP %d)";
    public static final String EV_FLOOR_ADVANCED_FORMAT = "%d 層に到達";

    /** Buff 適用ログ: %s = ActorId、%s = BuffKind 表示名、%+d = 符号付き量、%d = 残ターン数。 */
    public static final String EV_BUFF_APPLIED_FORMAT = "%s に %s %+d (残 %d ターン)";

    /** BuffKind の日本語表示名。 */
    public static final String BUFF_KIND_PHYSICAL_ATTACK = "物攻";

    public static final String BUFF_KIND_MAGICAL_ATTACK = "魔攻";
    public static final String BUFF_KIND_PHYSICAL_DEFENSE = "物防";
    public static final String BUFF_KIND_MAGICAL_DEFENSE = "魔防";
    public static final String BUFF_KIND_SPEED = "速度";

    // §15-4 ステータス確認ポップアップ (Tab キーで開閉)
    public static final String STATUS_TITLE = "ステータス";
    public static final String STATUS_PHYSICAL_ATTACK = "物攻";
    public static final String STATUS_MAGICAL_ATTACK = "魔攻";
    public static final String STATUS_PHYSICAL_DEFENSE = "物防";
    public static final String STATUS_MAGICAL_DEFENSE = "魔防";
    public static final String STATUS_HINT = "[Tab / ESC] 閉じる";
    public static final String STATUS_EQUIPMENT_HEADER = "── 装備 ──";
    public static final String STATUS_EQUIP_NONE = "(なし)";
    public static final String EQUIP_SLOT_FEET = "足";
    public static final String EQUIP_SLOT_HAND = "手";
    public static final String EQUIP_SLOT_MAIN = "主武器";
    public static final String EQUIP_SLOT_HEAD = "頭";
    public static final String EQUIP_SLOT_BODY = "胴";
    public static final String EQUIP_SLOT_ACCESSORY = "装飾";

    // §15-2 / §15-9 金貨 (ラン内通貨)
    public static final String HUD_GOLD = "金貨";
    public static final String EV_GOLD_GAINED_FORMAT = "%s は %d 金貨を獲得";

    // §15-3 / §15-6 強化個体撃破通知
    public static final String EV_ELITE_DEFEATED_FORMAT = "%s を撃破! カード追加チャンス";

    // Wave 11 W11-α 壊れる壁破壊通知
    public static final String EV_WALL_BROKEN_FORMAT = "(%d, %d) の壁を破壊した";

    // §15-7 / E-2 ソウルツリー画面 / タイトル画面動線
    public static final String SOUL_COST_FORMAT = "ソウル %d";
    public static final String SOUL_TREE_TITLE = "ソウルツリー";
    public static final String SOUL_TREE_INVENTORY_FORMAT = "所持ソウル: %d";
    public static final String SOUL_TREE_CONTROLS_HINT =
        "[クリック] 解放  [WASD/矢印] 移動  [Z/X] ズーム  [R] リセット  [ESC] 戻る";
    public static final String SOUL_TREE_FLASH_RESET = "ツリーをリセットしました";
    public static final String TITLE_OPEN_TREE_HINT_FORMAT = "[T] ソウルツリーを開く (所持ソウル: %d)";

    // §15-7 W6-α: ソウルツリー解放失敗時の flash メッセージ
    public static final String SOUL_TREE_FLASH_INSUFFICIENT_SOUL = "ソウルが不足しています";
    public static final String SOUL_TREE_FLASH_PREREQUISITE_NOT_UNLOCKED = "前提ノードが未解放です";
    public static final String SOUL_TREE_FLASH_CANNOT_UNLOCK = "解放できません";
    public static final String SOUL_TREE_FLASH_INVALID_NODE = "無効なノードです";

    // §15-9 W6-α: EquipmentScreen の StatsBonus 表示用短縮ラベル (STATUS_* とは別、bonusText 専用)
    public static final String BONUS_STAT_SPEED_SHORT = "速";
    public static final String BONUS_STAT_PHYSICAL_ATTACK_SHORT = "物攻";
    public static final String BONUS_STAT_MAGICAL_ATTACK_SHORT = "魔攻";
    public static final String BONUS_STAT_PHYSICAL_DEFENSE_SHORT = "物防";
    public static final String BONUS_STAT_MAGICAL_DEFENSE_SHORT = "魔防";

    // §15-8 W8-α: LayerEndNode のラベル (旧 domain 層 displayName を presentation に分離)
    public static final String LAYER_END_HP_MAX_UP_FORMAT = "HP +%d";
    public static final String LAYER_END_SPEED_UP_FORMAT = "速度 +%d";
    public static final String LAYER_END_REST = "HP を最大値の 1/3 回復";
    public static final String LAYER_END_SHOP_FORMAT = "ショップ: %s (金貨 %d)";
    public static final String LAYER_END_SHOP_EQUIPMENT_FORMAT = "装備購入: %s (金貨 %d)";
    public static final String EVENT_HEALING_SPRING = "治療の泉 (HP +20 / ソウル -10)";
    public static final String EVENT_GOLDEN_CHEST = "黄金の宝箱 (金貨 +50)";
    public static final String EVENT_SOUL_SHRINE = "ソウルの祠 (ソウル +30 / HP -5)";

    // §15-3 カード図鑑
    public static final String COLLECTION_TITLE = "カード図鑑";
    public static final String COLLECTION_LOCKED = "？？？ (未入手)";
    public static final String COLLECTION_HINT = "[↑↓ / WS] スクロール   [ESC] タイトルへ戻る";
    public static final String TITLE_OPEN_COLLECTION_HINT = "[C] カード図鑑を開く";

    // §15-9 装備変更画面
    public static final String EQUIP_SCREEN_TITLE = "装備の変更";
    public static final String EQUIP_SCREEN_HINT =
        "[↑↓ / WS] スクロール   [クリック] 装備 / 解除   [ESC] タイトルへ戻る";
    public static final String EQUIP_EQUIPPED_MARK = "★装備中";
    public static final String TITLE_OPEN_EQUIP_HINT = "[E] 装備を変更する";

    // §15-11 セーブ / ロード
    public static final String CONTINUE_HINT = "[L] つづきから";

    // SAVE-03B: 進行中ランがある状態で新規開始しようとしたときの確認。
    public static final String ABANDON_RUN_TITLE = "進行中の冒険があります";
    public static final String ABANDON_RUN_BODY =
        "新しく始めると進行中の冒険を放棄します。放棄した冒険は死亡と同じ扱いになります。" + "続きから再開する場合は [N] / [ESC] で戻ってから [L] を押してください。";
    public static final String ABANDON_RUN_HINT = "[Y] 放棄して新規開始   [N] / [ESC] やめる";

    // §15-10 / E-10 チュートリアル
    public static final String TUTORIAL_TITLE = "操作説明";
    public static final String TUTORIAL_BODY =
        """
        【移動】 WASD / 矢印キー / マップクリック で 1 マス移動 (1 AP 消費)

        【カード使用】
          1. 数字キー (1-9) or 手札クリック でカード選択
          2. WASD / 矢印キー / マップクリック で発射方向を指定 → カード使用
          ESC で選択キャンセル、同じ数字キーで選択解除

        【移動カード】 ダッシュ等は AP 消費で「移動権」を貯め、
                       その後 WASD / マップクリックで 1 マスずつ進む

        【待機】 SPACE でその場に留まる (AP 1 消費)

        【ターン終了】 ENTER / 画面右下「ターン終了」ボタン

        階段 (黄色) に到達で次層へ。敵撃破でソウル + 金貨獲得。
        層末でステ強化 / ショップ / 休憩 / イベント を選択。""";
    public static final String TUTORIAL_CLOSE_HINT = "ENTER / ESC / クリック で閉じる";

    // §15-1 / §15-8 設定画面
    public static final String SETTINGS_TITLE = "設定";
    public static final String SETTINGS_BGM_VOLUME = "BGM 音量";
    public static final String SETTINGS_SE_VOLUME = "SE 音量";
    public static final String SETTINGS_FULLSCREEN = "フルスクリーン";
    public static final String SETTINGS_UI_PRESET = "UI プリセット";
    // Wave 17 W17-α / #8 テーマ (ライト / ダーク 2 トグル)
    public static final String SETTINGS_THEME = "テーマ";
    public static final String SETTINGS_THEME_LIGHT = "ライト";
    public static final String SETTINGS_THEME_DARK = "ダーク";
    public static final String SETTINGS_ON = "ON";
    public static final String SETTINGS_OFF = "OFF";
    public static final String SETTINGS_RESET_SAVE = "セーブデータを初期化";
    public static final String SETTINGS_RESET_SAVE_VALUE = "ENTER で実行";
    public static final String SETTINGS_HINT =
        "↑↓ / WS: 項目選択   ←→ / AD: 値変更   ENTER: 実行   ESC: 戻る";
    public static final String TITLE_OPEN_SETTINGS_HINT = "[S] 設定";

    // 来場者交代用セーブデータ初期化の確認ダイアログ
    public static final String RESET_SAVE_CONFIRM_TITLE = "セーブデータを初期化しますか?";
    public static final String RESET_SAVE_CONFIRM_BODY =
        "ソウル・ソウルツリー・図鑑・装備・周回数・進行中の冒険をすべて削除し、はじめての状態に戻します。"
            + "音量などの設定は残ります。この操作は取り消せません。";
    public static final String RESET_SAVE_CONFIRM_HINT = "[Y] 初期化する   [N] / [ESC] やめる";
    public static final String RESET_SAVE_FAILED_MESSAGE =
        "セーブデータの初期化に失敗しました (ファイルを削除できませんでした)";

    // §15-1 初回 UI プリセット選択
    public static final String FIRST_RUN_PRESET_TITLE = "UI プリセットを選択してください";
    public static final String FIRST_RUN_PRESET_HINT = "1 / 2 / 3 キーで選択";

    // クレジット画面 (M2 提出)
    public static final String CREDITS_TITLE = "クレジット";
    public static final String CREDITS_BACK_HINT = "[ESC] タイトルへ戻る";
    public static final String TITLE_OPEN_CREDITS_HINT = "[K] クレジット";

    // §15-5 詰み回避: 自動ターン終了通知
    public static final String EV_AUTO_TURN_END_FORMAT = "自動ターン終了 (%s)";
    public static final String AUTO_TURN_END_REASON_STUCK = "行動不能";

    // §UI 改善: 敵のターン中の常時表示 + 画面外で敵が動いた時の通知
    public static final String HINT_ENEMY_TURN_IN_PROGRESS = "敵のターン進行中…";
    public static final String LOG_DISTANT_ENEMY_MOVE = "どこかで敵が動いているようだ";

    // §UI 改善 (Wave 1 Task 4): ソウルツリー以外の通知/フラッシュメッセージ i18n
    public static final String SHOP_INSUFFICIENT_GOLD_FORMAT = "金貨が足りません (必要 %d)";
    public static final String EVENT_INSUFFICIENT_SOUL_FORMAT = "ソウルが足りません (必要 %d)";
    public static final String LAYER_END_NO_AFFORDABLE_FLASH = "支払える選択肢がないため、何も得ずに次の層へ進みます";
    public static final String LAYER_SAVE_FAILED_FLASH = "保存に失敗しました（進行は続行できます）";
    public static final String CARD_REWARD_GAINED_FORMAT = "カード獲得: %s";
    public static final String ELITE_CARD_REWARD_TITLE = "強化個体撃破: カード追加";
    public static final String SOUL_TREE_UNLOCKED_FORMAT = "解放: %s";

    // W4-δ 敵図鑑画面 (BestiaryScreen)
    public static final String BESTIARY_TITLE = "敵 図鑑";
    public static final String BESTIARY_LOCKED = "??? (未撃破)";
    public static final String BESTIARY_HINT = "[↑↓ / WS] スクロール   [ESC] タイトルへ戻る";
    public static final String TITLE_OPEN_BESTIARY_HINT = "[B] 図鑑";
  }

  /** 英語版 (フォント未配置時の fallback)。 */
  public static final class En {
    private En() {}

    // PLATFORM-01: 日本語 UI が既に "Deep Dead Dungeons" を使っているため画面表示名を揃える。
    // これは表示文字列の統一であり、プロジェクト名 (rootProject.name) と JAR 名は据え置く。
    public static final String TITLE = "Deep Dead Dungeons";
    public static final String SUBTITLE = "- A tactical roguelite forged by death -";
    public static final String START_HINT = "Press ENTER to descend";
    public static final String CONTROLS_HEADER = "Controls";
    public static final String CONTROLS_MOVE = "WASD / Arrows   Move (1 AP)";
    public static final String CONTROLS_SKILL =
        "1 - 9           Select card -> Arrow for direction";
    public static final String CONTROLS_WAIT = "SPACE           Wait (1 AP)";
    public static final String CONTROLS_END = "ENTER           End your turn";

    public static final String HUD_HP = "HP";
    public static final String HUD_AP = "AP";
    public static final String HUD_SOUL = "Soul";
    public static final String HUD_PHASE = "Phase";
    public static final String HUD_SPEED = "Speed";
    public static final String HUD_HINT = "Tab: Status   ENTER: End Turn";

    // Move token hint during movement mode (ADR-21 §15-5)
    public static final String MOVE_TOKEN_HINT =
        "Move tokens: WASD/Arrows to move, ENTER to end turn";

    public static final String PHASE_PLAYER = "YOUR TURN";
    public static final String PHASE_ENEMY = "ENEMY TURN";
    public static final String PHASE_GAMEOVER = "GAME OVER";
    public static final String PHASE_CLEARED = "CLEARED";
    public static final String PHASE_RUN_CLEARED = "DUNGEON CLEAR";

    public static final String GAME_OVER_HEADER = "YOU DIED";
    public static final String RUN_CLEARED_HEADER = "DUNGEON CLEARED";
    public static final String SOULS_KEPT = "Souls carried out: ";
    public static final String NEW_RUN_HINT = "Press ENTER to open the Soul Tree";
    public static final String GAMEOVER_SAVE_FAILED_MESSAGE =
        "Save failed. Progress has not been recorded yet.";
    public static final String GAMEOVER_SAVE_FAILED_DETAIL =
        "Please check free disk space and write permissions.";
    public static final String GAMEOVER_SAVE_FAILED_HINT =
        "ENTER: Retry   ESC: Exit without saving";
    public static final String GAMEOVER_INVALID_STATE_MESSAGE =
        "Settlement aborted due to a data integrity error. Saved data was not modified.";
    public static final String GAMEOVER_INVALID_STATE_HINT = "ESC: Exit";

    // Hint shown after stepping on stairs (§15-6 / §15-8 / E-6).
    // Popup overlays the screen; HUD hint area also shows the same info for redundancy.
    public static final String CLEARED_HINT = "Layer-end node: press 1, 2, or 3";

    // Title shown on the layer-end node selection popup (§15-8 / E-6)
    public static final String LAYER_END_TITLE = "Layer-end node — choose one";

    // 手札表示ラベル
    public static final String HAND_HINT =
        "Number/Click: select  WASD/Arrow/Click: direction  ESC: cancel";
    public static final String CARD_ELEMENT_PHYSICAL = "Physical";
    public static final String CARD_ELEMENT_MAGICAL = "Magical";

    // 移動権ラベル (ADR-21 §15-5: remaining free-move steps granted by a Move card)
    public static final String MOVE_TOKEN_REMAINING_FORMAT = "Move tokens: %d";

    public static final String EV_MOVED_FORMAT = "%s moved to (%d, %d)";
    public static final String EV_SKILL_USED_FORMAT = "%s used %s";
    public static final String EV_DAMAGE_FORMAT = "%s -> %s : %d damage (HP %d left)";
    public static final String EV_DIED_FORMAT = "%s died";
    public static final String EV_SOUL_GAINED_FORMAT = "%s gained %d soul";
    public static final String EV_PHASE_FORMAT = "Phase -> %s";
    public static final String EV_REJECTED_FORMAT = "%s: rejected (%s)";
    public static final String EV_MOVEMENT_GRANTED_FORMAT = "%s gains %d move step(s)";
    public static final String EV_TRAP_PLACED_FORMAT = "%s placed a trap at (%d, %d)";
    public static final String EV_TRAP_TRIGGERED_FORMAT =
        "%s stepped on a trap: %d damage (HP %d left)";
    public static final String EV_FLOOR_ADVANCED_FORMAT = "Reached floor %d";

    /** Buff applied log: %s = ActorId, %s = BuffKind label, %+d = signed amount, %d = remaining. */
    public static final String EV_BUFF_APPLIED_FORMAT = "%s buffed: %s %+d (%d turns left)";

    /** BuffKind English label. */
    public static final String BUFF_KIND_PHYSICAL_ATTACK = "PhyAtk";

    public static final String BUFF_KIND_MAGICAL_ATTACK = "MagAtk";
    public static final String BUFF_KIND_PHYSICAL_DEFENSE = "PhyDef";
    public static final String BUFF_KIND_MAGICAL_DEFENSE = "MagDef";
    public static final String BUFF_KIND_SPEED = "Speed";

    // §15-4 status check popup (toggle with Tab)
    public static final String STATUS_TITLE = "Status";
    public static final String STATUS_PHYSICAL_ATTACK = "Phys Atk";
    public static final String STATUS_MAGICAL_ATTACK = "Mag Atk";
    public static final String STATUS_PHYSICAL_DEFENSE = "Phys Def";
    public static final String STATUS_MAGICAL_DEFENSE = "Mag Def";
    public static final String STATUS_HINT = "[Tab / ESC] Close";
    public static final String STATUS_EQUIPMENT_HEADER = "-- Equipment --";
    public static final String STATUS_EQUIP_NONE = "(none)";
    public static final String EQUIP_SLOT_FEET = "Feet";
    public static final String EQUIP_SLOT_HAND = "Hand";
    public static final String EQUIP_SLOT_MAIN = "Main";
    public static final String EQUIP_SLOT_HEAD = "Head";
    public static final String EQUIP_SLOT_BODY = "Body";
    public static final String EQUIP_SLOT_ACCESSORY = "Accessory";

    // §15-2 / §15-9 Gold (run-local currency)
    public static final String HUD_GOLD = "Gold";
    public static final String EV_GOLD_GAINED_FORMAT = "%s gained %d gold";

    // §15-3 / §15-6 Elite defeated notification
    public static final String EV_ELITE_DEFEATED_FORMAT =
        "Elite defeated: %s! Card reward unlocked";

    // Wave 11 W11-α Breakable wall destruction
    public static final String EV_WALL_BROKEN_FORMAT = "Broke the wall at (%d, %d)";

    // §15-7 / E-2 Soul Tree screen / title screen entry
    public static final String SOUL_COST_FORMAT = "Soul %d";
    public static final String SOUL_TREE_TITLE = "Soul Tree";
    public static final String SOUL_TREE_INVENTORY_FORMAT = "Owned Soul: %d";
    public static final String SOUL_TREE_CONTROLS_HINT =
        "[Click] Unlock  [WASD/Arrows] Pan  [Z/X] Zoom  [R] Reset  [ESC] Back";
    public static final String SOUL_TREE_FLASH_RESET = "Tree reset";
    public static final String TITLE_OPEN_TREE_HINT_FORMAT = "[T] Open Soul Tree (Soul: %d)";

    // §15-7 W6-α: Soul Tree unlock failure flash messages
    public static final String SOUL_TREE_FLASH_INSUFFICIENT_SOUL = "Insufficient soul";
    public static final String SOUL_TREE_FLASH_PREREQUISITE_NOT_UNLOCKED =
        "Prerequisite not unlocked";
    public static final String SOUL_TREE_FLASH_CANNOT_UNLOCK = "Cannot unlock";
    public static final String SOUL_TREE_FLASH_INVALID_NODE = "Invalid node";

    // §15-9 W6-α: EquipmentScreen StatsBonus short labels (separate from STATUS_*, bonusText only)
    public static final String BONUS_STAT_SPEED_SHORT = "SPD";
    public static final String BONUS_STAT_PHYSICAL_ATTACK_SHORT = "PAtk";
    public static final String BONUS_STAT_MAGICAL_ATTACK_SHORT = "MAtk";
    public static final String BONUS_STAT_PHYSICAL_DEFENSE_SHORT = "PDef";
    public static final String BONUS_STAT_MAGICAL_DEFENSE_SHORT = "MDef";

    // §15-8 W8-α: LayerEndNode labels (split from domain layer displayName)
    public static final String LAYER_END_HP_MAX_UP_FORMAT = "HP +%d";
    public static final String LAYER_END_SPEED_UP_FORMAT = "Speed +%d";
    public static final String LAYER_END_REST = "Heal 1/3 of max HP";
    public static final String LAYER_END_SHOP_FORMAT = "Shop: %s (Gold %d)";
    public static final String LAYER_END_SHOP_EQUIPMENT_FORMAT = "Equip: %s (Gold %d)";
    public static final String EVENT_HEALING_SPRING = "Healing Spring (HP +20 / Soul -10)";
    public static final String EVENT_GOLDEN_CHEST = "Golden Chest (Gold +50)";
    public static final String EVENT_SOUL_SHRINE = "Soul Shrine (Soul +30 / HP -5)";

    // §15-3 Card collection
    public static final String COLLECTION_TITLE = "Card Collection";
    public static final String COLLECTION_LOCKED = "??? (locked)";
    public static final String COLLECTION_HINT = "[Up/Down / WS] Scroll   [ESC] Back to title";
    public static final String TITLE_OPEN_COLLECTION_HINT = "[C] Open Card Collection";

    // §15-9 Equipment screen
    public static final String EQUIP_SCREEN_TITLE = "Change Equipment";
    public static final String EQUIP_SCREEN_HINT =
        "[Up/Down / WS] Scroll   [Click] Equip / Unequip   [ESC] Back to title";
    public static final String EQUIP_EQUIPPED_MARK = "[equipped]";
    public static final String TITLE_OPEN_EQUIP_HINT = "[E] Change Equipment";

    // §15-11 Save / Load
    public static final String CONTINUE_HINT = "[L] Continue";

    // SAVE-03B: confirmation shown when starting a new run while one is in progress.
    public static final String ABANDON_RUN_TITLE = "A run is still in progress";
    public static final String ABANDON_RUN_BODY =
        "Starting fresh abandons the run in progress. An abandoned run is settled as a death. "
            + "Press [N] / [ESC] to go back, then press [L] to resume it.";
    public static final String ABANDON_RUN_HINT = "[Y] Abandon and start   [N] / [ESC] Cancel";

    // §15-10 / E-10 Tutorial
    public static final String TUTORIAL_TITLE = "How to Play";
    public static final String TUTORIAL_BODY =
        """
        [Move] WASD / Arrows / Click map  - Move 1 tile (1 AP)

        [Use a Card]
          1. Press 1-9 or click a card to select
          2. Press WASD / arrow key or click map to fire
          ESC to cancel, same number key to deselect

        [Movement Cards] Dash etc. spend AP to grant move tokens,
                          then WASD / map click moves 1 tile each

        [Wait] SPACE to stay in place (costs 1 AP)

        [End Turn] ENTER / "End Turn" button (bottom right)

        Reach the yellow stairs to descend.
        Defeat enemies to earn Soul + Gold.
        Pick a node at layer end: stat / shop / rest / event.""";
    public static final String TUTORIAL_CLOSE_HINT = "ENTER / ESC / Click to close";

    // §15-1 / §15-8 Settings screen
    public static final String SETTINGS_TITLE = "Settings";
    public static final String SETTINGS_BGM_VOLUME = "BGM Volume";
    public static final String SETTINGS_SE_VOLUME = "SE Volume";
    public static final String SETTINGS_FULLSCREEN = "Fullscreen";
    public static final String SETTINGS_UI_PRESET = "UI Preset";
    // Wave 17 W17-α / #8 Theme (Light / Dark 2-toggle)
    public static final String SETTINGS_THEME = "Theme";
    public static final String SETTINGS_THEME_LIGHT = "Light";
    public static final String SETTINGS_THEME_DARK = "Dark";
    public static final String SETTINGS_ON = "ON";
    public static final String SETTINGS_OFF = "OFF";
    public static final String SETTINGS_RESET_SAVE = "Reset Save Data";
    public static final String SETTINGS_RESET_SAVE_VALUE = "Press ENTER";
    public static final String SETTINGS_HINT =
        "Up/Down / WS: navigate   Left/Right / AD: change value   ENTER: execute   ESC: back";
    public static final String TITLE_OPEN_SETTINGS_HINT = "[S] Settings";

    // Visitor handoff save reset confirmation dialog
    public static final String RESET_SAVE_CONFIRM_TITLE = "Reset all save data?";
    public static final String RESET_SAVE_CONFIRM_BODY =
        "This will delete all Soul, Soul Tree progress, collections, equipment, run count, "
            + "and active runs. Audio and display settings will be kept. This cannot be undone.";
    public static final String RESET_SAVE_CONFIRM_HINT = "[Y] Reset all   [N] / [ESC] Cancel";
    public static final String RESET_SAVE_FAILED_MESSAGE =
        "Failed to reset save data (could not delete save files)";

    // §15-1 First-run UI preset selection
    public static final String FIRST_RUN_PRESET_TITLE = "Choose a UI preset";
    public static final String FIRST_RUN_PRESET_HINT = "Press 1 / 2 / 3 to select";

    // Credits screen (M2 submission)
    public static final String CREDITS_TITLE = "Credits";
    public static final String CREDITS_BACK_HINT = "[ESC] Back to title";
    public static final String TITLE_OPEN_CREDITS_HINT = "[K] Credits";

    // §15-5 stuck-handling: auto turn end notification
    public static final String EV_AUTO_TURN_END_FORMAT = "Auto turn end (%s)";
    public static final String AUTO_TURN_END_REASON_STUCK = "stuck";

    // §UI: Enemy-turn always-on indicator + off-screen enemy move notification
    public static final String HINT_ENEMY_TURN_IN_PROGRESS = "Enemy turn in progress…";
    public static final String LOG_DISTANT_ENEMY_MOVE = "An enemy is moving somewhere";

    // §UI improvement (Wave 1 Task 4): Non-SoulTree notification / flash i18n
    public static final String SHOP_INSUFFICIENT_GOLD_FORMAT = "Not enough gold (need %d)";
    public static final String EVENT_INSUFFICIENT_SOUL_FORMAT = "Not enough soul (need %d)";
    public static final String LAYER_END_NO_AFFORDABLE_FLASH =
        "Nothing affordable - moving on to the next floor with no reward";
    public static final String LAYER_SAVE_FAILED_FLASH = "Save failed (you can continue playing)";
    public static final String CARD_REWARD_GAINED_FORMAT = "Card gained: %s";
    public static final String ELITE_CARD_REWARD_TITLE = "Elite defeated: Card reward";
    public static final String SOUL_TREE_UNLOCKED_FORMAT = "Unlocked: %s";

    // W4-δ Bestiary screen (BestiaryScreen)
    public static final String BESTIARY_TITLE = "Bestiary";
    public static final String BESTIARY_LOCKED = "??? (locked)";
    public static final String BESTIARY_HINT = "[Up/Down / WS] Scroll   [ESC] Back to title";
    public static final String TITLE_OPEN_BESTIARY_HINT = "[B] Bestiary";
  }
}
