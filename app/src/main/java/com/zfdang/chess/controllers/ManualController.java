package com.zfdang.chess.controllers;

import android.content.Context;
import android.util.Log;

import com.zfdang.chess.ChessApp;
import com.zfdang.chess.R;
import com.zfdang.chess.gamelogic.Board;
import com.zfdang.chess.gamelogic.GameStatus;
import com.zfdang.chess.gamelogic.Move;
import com.zfdang.chess.gamelogic.Piece;
import com.zfdang.chess.gamelogic.Position;
import com.zfdang.chess.gamelogic.PvInfo;
import com.zfdang.chess.manuals.XQFManual;
import com.zfdang.chess.manuals.XQFParser;
import com.zfdang.chess.utils.PathUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class ManualController extends GameController{
    public XQFManual manual = null;
    public XQFManual.MoveNode moveNode = null;

    private Context context = null;
    private ControllerListener gui = null;

    public ManualController(ControllerListener listener) {
        super(listener);
        this.gui = listener;
        this.context = ChessApp.getContext();
    }

    public boolean loadManualFromFile(String filename) {
        if(filename.toLowerCase().endsWith(".xqf")) {
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(new File(filename));
                byte[] buffer = new byte[inputStream.available()];
                inputStream.read(buffer);
                inputStream.close();

                // use XQFGame to parse the buffer
                manual = XQFParser.parse(buffer);
                if(manual == null) {
                    Log.e("ManualActivity", "Failed to parse XQF game: " + filename);
                    gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_parse_xqf_failed, filename));
                    return false;
                }

                manual.setFilename(PathUtil.getFileName(filename));

                boolean result = manual.validateAllMoves();
                if (!result) {
                    Log.e("ManualActivity", "Failed to validate moves: "  + filename);
                }

                Log.d("ManualActivity", "Parsed XQF game: " + manual);

                // 根据XQFManual的getHeadMove()方法获取第一个MoveNode
                moveNode = manual.getHeadMove();

                // 根据第一个move的颜色，来确定那方先走
                if(moveNode.nextMoves.size() > 0) {
                    Move m = moveNode.nextMoves.get(0).move;
                    Position p = m.fromPosition;
                    int piece = manual.board.getPieceByPosition(p);
                    manual.board.bRedGo = Piece.isRed(piece);
                    setSatate(manual.board.bRedGo);
                }

                // reset game
                game.currentBoard = new Board(manual.board);
                game.clearHistory();
                game.clearSuggestedMoves();
                game.startPos = null;
                game.endPos = null;

                return true;
            } catch (IOException e) {
                Log.e("ManualController", "Failed to load manual from file: " + filename);
                Log.e("ManualController", e.getMessage());
            }
        }

        return false;
    }

    public void manualForward() {
        if(manual == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_manual_not_open));
            return;
        }

        if(moveNode == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_abnormal_state));
            return;
        }

        ArrayList<XQFManual.MoveNode> children = moveNode.nextMoves;
        if(children.size() == 1){
            // only one child, move to it
            moveNode = children.get(0);
            if(moveNode != null) {
                Move m = moveNode.move;
                if(m != null) {
                    game.startPos = m.fromPosition;
                    game.endPos = m.toPosition;
                    game.movePiece();
                    toggleTurn();

                    gui.onGameEvent(GameStatus.MOVE, game.getLastMoveDesc());
                }
            }
        } else if(children.size() > 1){
            // multiple children, show a dialog to let user choose
            ArrayList<PvInfo> choices = new ArrayList<>();
            for(XQFManual.MoveNode child : children){
                PvInfo pvInfo = new PvInfo(0, 0, 0, 0, 0, 0, 0, 0, false, false, false, new ArrayList<>());
                pvInfo.pv.add(child.move);
                choices.add(pvInfo);
            }
            suggestedPVs = java.util.Collections.unmodifiableList(choices);
            game.generateSuggestedMoves(suggestedPVs);
            gui.onGameEvent(GameStatus.MULTIPV, ChessApp.str(R.string.msg_choose_branch));
            Log.d("ManualController", "multiPVs: " + suggestedPVs.size());
        } else{
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_no_next_move));
        }

    }

    public void manualBack() {
        if(manual == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_manual_not_open));
            return;
        }

        if(moveNode == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_abnormal_state));
            return;
        }

        if(moveNode.parent == null) {
            String hint = ChessApp.str(R.string.msg_at_start, getFirstMoveColor());
            game.clearSuggestedMoves();
            gui.onGameEvent(GameStatus.MOVE, hint);
            return;
        }

        moveNode = moveNode.parent;
        game.undoMove();
        toggleTurn();
        gui.onGameEvent(GameStatus.MOVE, ChessApp.str(R.string.msg_back_one_move));
    }

    public String getFirstMoveColor(){
        if(manual == null) {
            return ChessApp.str(R.string.msg_manual_not_open);
        }

        if(manual.board.bRedGo) {
            return ChessApp.str(R.string.msg_red_first);
        } else {
            return ChessApp.str(R.string.msg_black_first);
        }
    }

    public void manualFirst() {
        if(manual == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_manual_not_open));
            return;
        }

        moveNode = manual.getHeadMove();

        // reset game
        game.currentBoard = new Board(manual.board);
        game.clearHistory();
        game.clearSuggestedMoves();
        game.startPos = null;
        game.endPos = null;

        setSatate(manual.board.bRedGo);

        String hint = ChessApp.str(R.string.msg_back_to_start, getFirstMoveColor());
        gui.onGameEvent(GameStatus.MOVE, hint);
    }

    public void selectBranch(int i) {
        if(manual == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_manual_not_open));
            return;
        }

        if(moveNode == null) {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_abnormal_state));
            return;
        }

        ArrayList<XQFManual.MoveNode> children = moveNode.nextMoves;
        if(i < children.size()) {
            moveNode = children.get(i);
            if(moveNode != null) {
                Move m = moveNode.move;
                if(m != null) {
                    game.startPos = m.fromPosition;
                    game.endPos = m.toPosition;
                    game.movePiece();
                    toggleTurn();

                    game.clearSuggestedMoves();
                    gui.onGameEvent(GameStatus.MOVE, ChessApp.str(R.string.msg_branch, i+1, game.getLastMoveDesc()));
                }
            }
        } else {
            gui.onGameEvent(GameStatus.ILLEGAL, ChessApp.str(R.string.msg_invalid_branch, i+1));
        }
    }
}
