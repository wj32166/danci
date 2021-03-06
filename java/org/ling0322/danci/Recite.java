package org.ling0322.danci;

import java.util.*;
import android.util.Pair;

class UpdateDatabase implements Runnable {
    private ArrayList<Pair<String, Boolean>> answerList;
    private WordlistDB wl;
    public UpdateDatabase(ArrayList<Pair<String, Boolean>> answerList) {
        this.answerList = answerList;
        this.wl = new WordlistDB();
    }
    public void run() {
        wl.answerList(answerList);
    }
    
}

public class Recite {

    private final int ST_FINISHED = 0;
    private final int ST_NEW = 1;
    private final int ST_REVIEW = 2;
    private final int ST_DRILL = 3;
    
    private int mWordsPerUnit = 30;

    private Thread updateThread;
    private ArrayList<String> wordList = new ArrayList<String>();
    private ArrayList<String> drillList = new ArrayList<String>();
    private ArrayList<String> sqlUpdateBuffer= new ArrayList<String>();
    private int cntState = ST_FINISHED;
    private String cntWord = "";
    private int totalWord = -1;
    private int mNewWordCount = 0;
    private int mReviewWordCount = 0;
    private WordlistDB wldb;
    private ArrayList<Pair<String, Boolean>> answerList;
    

    public Recite() {
        answerList = new ArrayList<Pair<String, Boolean>>();
        wldb = new WordlistDB();
        if (wldb.isNullDbConn() == true)
            return ;
        totalWord = wldb.totalWord();
    }
    

    public boolean isNullDbConn() {
        return wldb.isNullDbConn();
    }
    
    public void finallize() {
    }
    
    public String getCntState() {
        switch (cntState) {
        case ST_FINISHED:
            int unpass = wldb.getProgress();
            String cntwl = Wordlist.currentWordList();
            return String.format(
                "%s→当前进度: %d/%d %d%%",
                cntwl,
                totalWord - unpass,
                totalWord,
                (int)((totalWord - unpass) * 100 / totalWord)
                );
        case ST_NEW:
            return String.format("开始→[新单词 - %d/%d]→复习→巩固", wordList.size(), mNewWordCount);
        case ST_REVIEW:
            return String.format("开始→新单词→[复习 - %d/%d]→巩固", wordList.size(), mReviewWordCount);
        case ST_DRILL:
            return String.format("开始→新单词→复习→[巩固 - %d]", wordList.size());
        }
        
        return "";
    }
    
    public void finish() {
        cntState = ST_FINISHED;
    }
    
    public void start() {
        
        if (cntState == ST_FINISHED) {
            cntState = ST_NEW;
            
            //
            // calculate new word and review word numbers
            //
            double reviewWords = wldb.reviewCount();
            double reviewWordsNumber = Math.atan(reviewWords / 80) * mWordsPerUnit * 2 / Math.PI;
            mNewWordCount = mWordsPerUnit - (int)reviewWordsNumber;
            
            wordList.clear();
            wordList = wldb.newWordList(mNewWordCount);
            mNewWordCount = wordList.size();
            mReviewWordCount = mWordsPerUnit - mNewWordCount;
            
            switchState();
        }
    }
    
    private void switchState() {
        if (wordList.size() == 0) {
            switch(cntState) {
            case ST_NEW:
                wordList = wldb.reviewList(mReviewWordCount);
                mReviewWordCount = wordList.size();
                cntState = ST_REVIEW;
                switchState();
                break;
            case ST_REVIEW:
                wordList = drillList;
                cntState = ST_DRILL;
                //
                // During the drill period, there is no database IO, so we could
                // now start to write back to database
                //
                updateThread = new Thread(new UpdateDatabase(answerList));
                updateThread.start();
                sqlUpdateBuffer.clear();
                switchState();
                break;
            case ST_DRILL:
                cntState = ST_FINISHED;
                //
                // Here, we start to use database IO again, so we should wait updateThread
                // to finish HERE
                //
                try {
                    updateThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                drillList.clear();
                wordList.clear();
                answerList.clear();
            }
        }
    }
    
    public String pickWord() {
        cntWord = wordList.get(0);
        return cntWord;
    }
    
    public boolean isFinsihed() {
        if (cntState == ST_FINISHED) {
            return true;
        } else {
            return false;
        }
    }
    
    public void answer(boolean correct) {
        if (wordList.size() == 0)
            return ;
        
        if (cntState == ST_DRILL) {
            wordList.remove(0);
            if (correct == false) {
                wordList.add(cntWord);
            }
            switchState();
        } else {
            wordList.remove(0);
            if (correct == false)
                drillList.add(cntWord);
            answerList.add(new Pair<String, Boolean>(cntWord, correct));
            switchState();
        }
    }
}
