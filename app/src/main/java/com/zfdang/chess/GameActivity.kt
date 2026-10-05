package com.zfdang.chess

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.Description
import com.zfdang.chess.adapters.HistoryAndTrendAdapter
import com.zfdang.chess.controllers.GameController
import com.zfdang.chess.controllers.ControllerListener
import com.zfdang.chess.databinding.ActivityGameBinding
import com.zfdang.chess.gamelogic.GameStatus
import com.zfdang.chess.openbook.BHOpenBook
import com.zfdang.chess.views.ChessView


class GameActivity() : AppCompatActivity(), View.OnTouchListener, ControllerListener,
    View.OnClickListener, SettingDialogFragment.SettingDialogListener {

    // 防止重复点击
    private val MIN_CLICK_DELAY_TIME: Int = 100
    private var curClickTime: Long = 0
    private var lastClickTime: Long = 0

    private lateinit var binding: ActivityGameBinding
    private lateinit var chessLayout: FrameLayout

    // 棋盘
    private lateinit var chessView: ChessView
    private lateinit var historyAndTrendAdapter: HistoryAndTrendAdapter

    // controller, player, game
    private lateinit var controller: GameController

    // mediaplayer
    private lateinit var soundPlayer: SoundPlayer

    private lateinit var bhBook: BHOpenBook

    private var isFromManual = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable screen saver
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Insets change padding, not bounds, so the layout listener below would never fire for
        // keyboard, rotation or cutout changes. Recompute the board from the insets callback too.
        com.zfdang.chess.utils.WindowInsetsUtil.apply(this, binding.root) {
            binding.root.post { fitBoardToScreen() }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { saveThenExit() }
        })
        binding.morebt.setOnClickListener {
            val expanded = binding.advancedTools.visibility != View.VISIBLE
            binding.advancedTools.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.morebt.text = getString(if (expanded) R.string.tools_collapse else R.string.tools_expand)
        }

        // Size the board for the collapsed page; expanded tools may extend below the fold.
        binding.root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                binding.root.post { fitBoardToScreen() }
            }
        }

        // new game
        controller = GameController(this)
        controller.loadGameStatus()

        // 初始化棋盘
        chessLayout = binding.chesslayout
        chessView = ChessView(this, controller)
        chessLayout.addView(chessView)
        chessView.setOnTouchListener(this)

        bhBook = BHOpenBook(this)

        // Bind all imagebuttons here, and set their onClickListener
        val imageButtons = listOf(
            binding.playerbt,
            binding.playerbackbt,
            binding.playerforwardbt,
            binding.autoplaybt,
            binding.quickbt,
            binding.playeraltbt,
            binding.optionbt,
            binding.newbt,
            binding.backbt,
            binding.importbt,
            binding.exportbt,
            binding.helpbt,
            binding.stophelpbt,
            binding.trendsbt,
            binding.exitbt,
            binding.choice1bt,
            binding.choice2bt,
            binding.choice3bt
        )
        for (button in imageButtons) {
            button.setOnClickListener(this)
        }

        // init audio files
        soundPlayer = SoundPlayer(this, controller)

        // Bind historyTable and initialize it with dummy data
        val historyTable = binding.historyTable
        val chart = binding.trendchart
        historyAndTrendAdapter = HistoryAndTrendAdapter(this, historyTable, chart, controller)
        updateGameHistory()

        // customize chart
        chart.description = Description().apply {
            text = ""
        }
        val xAxis = chart.xAxis
        xAxis.setGranularityEnabled(true)
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        val yAxis = chart.axisLeft
        yAxis.setDrawGridLines(false)
        yAxis.setDrawZeroLine(true);
        val rightAxis = chart.axisRight
        rightAxis.setDrawGridLines(false)

        // init button status
        if(controller.isAutoPlay) {
            binding.autoplaybt.setImageResource(R.drawable.play_circle)
        } else {
            binding.autoplaybt.setImageResource(R.drawable.pause_circle)
        }
        if(controller.isComputerPlaying){
            binding.playerbt.setImageResource(R.drawable.computer)
        } else {
            binding.playerbt.setImageResource(R.drawable.person)
        }

        // init status text
        if(controller.isRedTurn){
            setStatusText(getString(R.string.status_wait_red))
        } else if(controller.isBlackTurn) {
            setStatusText(getString(R.string.status_wait_black))
        }

        // receive parameters from intent
        val fenString = intent.getStringExtra("FENString")
        if(fenString != null){
            controller.startFENGame(fenString)
            isFromManual = true
            controller.toggleComputerAutoPlay()
            binding.autoplaybt.setImageResource(R.drawable.pause_circle)
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        // A page drag is intercepted by the scroll view and cancels this tap.
        if (event.action != MotionEvent.ACTION_UP) return true
        lastClickTime = System.currentTimeMillis()
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) return true
        curClickTime = lastClickTime
        v?.performClick()
        val pos = chessView.getPosByCoord(event.x, event.y) ?: return true
        controller.touchPosition(pos)
        return true
    }

    private fun fitBoardToScreen() {
        val content = binding.gameContent
        var fixedHeight = content.paddingTop + content.paddingBottom
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child === binding.chesslayout || child === binding.advancedTools) continue
            val margins = child.layoutParams as ViewGroup.MarginLayoutParams
            fixedHeight += child.measuredHeight + margins.topMargin + margins.bottomMargin
        }
        val boardParams = binding.chesslayout.layoutParams as ViewGroup.MarginLayoutParams
        val available = binding.root.height - binding.root.paddingTop - binding.root.paddingBottom - fixedHeight - boardParams.topMargin - boardParams.bottomMargin
        val fullWidthHeight = (content.width - content.paddingLeft - content.paddingRight) * ChessView.BOARD_HEIGHT / ChessView.BOARD_WIDTH
        val height = minOf(fullWidthHeight, available.coerceAtLeast(0))
        if (boardParams.height != height) {
            boardParams.height = height
            binding.chesslayout.layoutParams = boardParams
        }
    }

    private fun updateGameHistory() {
        historyAndTrendAdapter.update()
        binding.historyTitle.text = getString(if (controller.isShowTrends) R.string.eval_title else R.string.history_title)
        binding.historyEmpty.visibility =
            if (controller.game.history.isEmpty() && !controller.isShowTrends) View.VISIBLE else View.GONE
        binding.trendchart.visibility = if (controller.isShowTrends) View.VISIBLE else View.GONE
        binding.historyscroll.visibility = if (controller.isShowTrends) View.GONE else View.VISIBLE
    }

    // create function to set status text
    fun setStatusText(text: String) {
        binding.statustv.text = text
        binding.opponentLabel.text = getString(if (controller.isComputerPlaying) R.string.opponent_computer else R.string.opponent_human)
    }

    fun showNewGameConfirmDialog() {
        val builder = MaterialAlertDialogBuilder(this)
            .setBackground(AppCompatResources.getDrawable(this, R.drawable.ui_dialog_background))
        builder.setTitle(R.string.new_game_title)
        builder.setMessage(R.string.new_game_message)

        builder.setPositiveButton(R.string.new_game_confirm) { dialog, which ->
            // User clicked Yes button
            controller.startNewGame()
            updateGameHistory()
            if(controller.settings.red_go_first) {
                setStatusText(getString(R.string.status_new_game_red))
            } else {
                setStatusText(getString(R.string.status_new_game_black))
            }
            // hide choice buttons
            if(binding.choice1bt.visibility == View.VISIBLE){
                binding.choice1bt.visibility = View.GONE;
                binding.choice2bt.visibility = View.GONE;
                binding.choice3bt.visibility = View.GONE;
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if(controller.isComputerPlaying && controller.isAutoPlay && !controller.settings.red_go_first){
                    controller.computerForward()
                }
            }, 1000)
        }

        builder.setNegativeButton(R.string.new_game_continue) { dialog, which ->
            // User clicked No button
            dialog.dismiss()
        }

        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.show()
    }

    fun saveThenExit() {
        controller.close()
        if(!isFromManual){
            // 如果从打谱界面进入，不保存游戏状态
            controller.saveGameStatus();
        }
        finish()
    }

    fun showInputFENDialog() {
        val builder = MaterialAlertDialogBuilder(this)
            .setBackground(AppCompatResources.getDrawable(this, R.drawable.ui_dialog_background))
        builder.setTitle(R.string.fen_dialog_title)
        builder.setMessage(R.string.fen_dialog_message)

        // Set up the input
        val inputView = layoutInflater.inflate(R.layout.dialog_fen, null)
        val input = inputView.findViewById<EditText>(R.id.fen_input)

        // get content from clipboard
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text
            input.setText(text)
        }
        input.setSelection(input.text.length)
        input.setSelectAllOnFocus(true)

        builder.setView(inputView)

        // Set up the buttons
        builder.setPositiveButton(R.string.fen_dialog_import, DialogInterface.OnClickListener { dialog, which ->
            val userInput = input.text.toString()
            controller.startFENGame(userInput)
            // Handle the input string here
        })
        builder.setNegativeButton(R.string.dialog_cancel, DialogInterface.OnClickListener { dialog, which ->
            dialog.cancel()
        })

        builder.show()
    }

    override fun onClick(v: View?) {
        // handle events for all imagebuttons in activity_player.xml
        when(v) {
            binding.playerbt -> {
                controller.toggleComputer()
                if(controller.isComputerPlaying){
                    binding.playerbt.setImageResource(R.drawable.computer)
                    setStatusText(getString(R.string.status_computer_black))
                } else {
                    binding.playerbt.setImageResource(R.drawable.person)
                    setStatusText(getString(R.string.status_human_black))
                }
            }
            binding.playerbackbt -> {
                controller.stepBack()
            }
            binding.playerforwardbt -> {
                controller.computerForward()
            }
            binding.autoplaybt -> {
                controller.toggleComputerAutoPlay()
                if(controller.isAutoPlay){
                    binding.autoplaybt.setImageResource(R.drawable.play_circle)
                    setStatusText(getString(R.string.status_auto_on))

                    if(controller.isBlackTurn() && controller.isComputerPlaying){
                        // 如果电脑执黑，自动走棋
                        controller.computerForward();
                    }
                } else {
                    binding.autoplaybt.setImageResource(R.drawable.pause_circle)
                    setStatusText(getString(R.string.status_auto_off))
                }
            }
            binding.quickbt -> {
                controller.stopSearchNow()
            }
            binding.playeraltbt -> {
                controller.computerAskForMultiPV();
            }
            binding.optionbt -> {
                // Show the dialog
                val dialog = SettingDialogFragment()
                dialog.setController(controller)
                dialog.listener = this
                dialog.setEngineInfo(controller.engineInfo)
                dialog.show(supportFragmentManager, "CustomDialog")
            }
            binding.newbt -> {
                // display dialog to ask users for confirmation
                showNewGameConfirmDialog()
            }
            binding.backbt -> {
                controller.stepBack()
            }
            binding.importbt -> {
                showInputFENDialog()
            }
            binding.exportbt -> {
                val fenString = controller.game.currentBoard.toFENString()
                // copy to clipboard
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("FEN", fenString)
                clipboard.setPrimaryClip(clip)
                setStatusText(getString(R.string.status_fen_copied))
                Log.d("GameActivity", "fenString: $fenString")
            }
            binding.helpbt -> {
                setStatusText(getString(R.string.status_searching_hints))
                controller.playerAskForHelp();
            }
            binding.stophelpbt -> {
                controller.stopSearchNow()
            }
            binding.trendsbt -> {
                // get image resource of trends button
                controller.toggleShowTrends()
                val imageResource = if(controller.isShowTrends) R.drawable.trend else R.drawable.history
                binding.trendsbt.setImageResource(imageResource)

                if(controller.isShowTrends){
                    setStatusText(getString(R.string.status_show_eval_chart))
                    binding.trendchart.visibility = View.VISIBLE
                    binding.historyscroll.visibility = View.GONE
                } else {
                    setStatusText(getString(R.string.status_show_move_history))
                    binding.trendchart.visibility = View.GONE
                    binding.historyscroll.visibility = View.VISIBLE
                }

                updateGameHistory()
            }
            binding.exitbt -> {
                saveThenExit();
            }
            binding.choice1bt -> {
                setStatusText(getString(R.string.status_select_move, 1))
                binding.choice1bt.visibility = View.GONE;
                binding.choice2bt.visibility = View.GONE;
                binding.choice3bt.visibility = View.GONE;
                controller.selectMultiPV(0)
            }
            binding.choice2bt -> {
                setStatusText(getString(R.string.status_select_move, 2))
                binding.choice1bt.visibility = View.GONE;
                binding.choice2bt.visibility = View.GONE;
                binding.choice3bt.visibility = View.GONE;
                controller.selectMultiPV(1)
            }
            binding.choice3bt -> {
                setStatusText(getString(R.string.status_select_move, 3))
                binding.choice1bt.visibility = View.GONE;
                binding.choice2bt.visibility = View.GONE;
                binding.choice3bt.visibility = View.GONE;
                controller.selectMultiPV(2)
            }
        }

    }

    override fun onGameEvent(status: GameStatus?, message: String?) {
        Log.d(  "PlayActivity", "onGameEvent: $status, $message")
        when(status) {
            GameStatus.ILLEGAL -> {
                message?.let { setStatusText(it) }
                soundPlayer.illegal();
            }
            GameStatus.MOVE -> {
                message?.let { setStatusText(it) }
                soundPlayer.move();

                if(binding.choice1bt.visibility == View.VISIBLE){
                    binding.choice1bt.visibility = View.GONE;
                    binding.choice2bt.visibility = View.GONE;
                    binding.choice3bt.visibility = View.GONE;
                }
            }
            GameStatus.CAPTURE -> {
                message?.let { setStatusText(it) }
                soundPlayer.capture()
            }
            GameStatus.CHECK -> {
                message?.let { setStatusText(it) }
                soundPlayer.check()
            }
            GameStatus.CHECKMATE -> {
                message?.let { setStatusText(it) }
                soundPlayer.checkmate()
            }
            GameStatus.SELECT -> {
                message?.let { setStatusText(it) }
                soundPlayer.select()
            }
            GameStatus.WIN -> message?.let { setStatusText(it) }
            GameStatus.LOSE -> message?.let { setStatusText(it) }
            GameStatus.DRAW -> message?.let { setStatusText(it) }
            GameStatus.ENGINE -> message?.let { setStatusText(it) }
            null -> TODO()
            GameStatus.MULTIPV -> {
                // show message
                message?.let { setStatusText(it) }

                // show choice buttons
                if(binding.choice1bt.visibility == View.GONE){
                    binding.choice1bt.visibility = View.VISIBLE;
                    if(controller.getMultiPVSize() >= 2){
                        binding.choice2bt.visibility = View.VISIBLE;
                    }
                    if(controller.getMultiPVSize() >= 3){
                        binding.choice3bt.visibility = View.VISIBLE;
                    }
                }

                soundPlayer.ready()
            }
            GameStatus.UPDATEUI -> {
                message?.let { setStatusText(message) }
                // do nothing here
            }
        }

        // update history table
        updateGameHistory()
    }

    // create fun to handle onbackpressed
    override fun onDestroy() {
        if (::controller.isInitialized) controller.close()
        if (::soundPlayer.isInitialized) soundPlayer.release()
        super.onDestroy()
    }

    override fun onGameEvent(event: GameStatus?) {
        onGameEvent(event, null);
    }

    override fun runOnUIThread(runnable: Runnable?) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) runnable?.run()
        }
    }

    override fun onDialogPositiveClick() {
        // save setting values to variables in settings
        Log.d("GameActivity", "onDialogPositiveClick" + controller.settings.toString())
    }

    override fun onDialogNegativeClick() {
        // do nothing
    }

}
