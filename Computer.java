public class Computer {

    private int nodesTraversed = 0;

    // The psuedocode of the minimax and alphabeta pruning has been taken from the Wikipedia links below:  
    // https://en.wikipedia.org/wiki/Alpha%E2%80%93beta_pruning#Pseudocode
    // https://en.wikipedia.org/wiki/Minimax#Pseudocode
    public int[] computeColumn(boolean alphaBeta, Board b, int depth, int alpha, int beta, boolean maxPlayer) {
        this.nodesTraversed++;
        if (numInARow(4, b, 'r') > 0 || numInARow(5, b, 'r') > 0 || numInARow(6, b, 'r') > 0 || numInARow(7, b, 'r') > 0) {
            return new int[] {-1, 10000};
        } else if (numInARow(4, b, 'y') > 0 || numInARow(5, b, 'y') > 0 || numInARow(6, b, 'y') > 0 || numInARow(7, b, 'y') > 0) {
            return new int[] {-1, -10000};
        } else if (depth == 0) {
            return new int[] {-1, evaluation(b)};
        }
        int column = -1;
        int value; 
        if (maxPlayer) {
            value = Integer.MIN_VALUE;
            for (int i = 0; i < 7; i++) {
                Board tempBoard = b.getCopy();
                if (tempBoard.putCounter(i, 'r')) {
                    int[] thisValue = computeColumn(alphaBeta, tempBoard, depth - 1, alpha, beta, false);
                    if (thisValue[1] > value) {
                        value = thisValue[1]; 
                        column = i;
                    }
                    if (alphaBeta) {
                        if (thisValue[1] > alpha) {alpha = thisValue[1];}
                        if (alpha >= beta) {
                            break;
                        }
                    }
                }
            }
        } else {
            value = Integer.MAX_VALUE;
            for (int i = 0; i < 7; i++) {
                Board tempBoard = b.getCopy();
                if (tempBoard.putCounter(i, 'y')) {
                    int[] thisValue = computeColumn(alphaBeta, tempBoard, depth - 1, alpha, beta, true);
                    if (thisValue[1] < value) {value = thisValue[1]; column = i;}
                    if (alphaBeta) {
                        if (thisValue[1] < beta) beta = thisValue[1];
                        if (alpha >= beta) {
                            break;
                        }
                    }
                }
            }
        }
        return new int[] {column, value};
    }

    public int getNodesTraversed() {
        return nodesTraversed;
    }
    
    private int evaluation(Board b) {
        return score('r', b) - score('y', b);
    }

    private int score(char player, Board b) {
        int tokensOnBoard;
        if (player == 'r')
            tokensOnBoard = b.getTokenCountRed();
        else
            tokensOnBoard = b.getTokenCountYellow();
        return tokensOnBoard +
                10 * numInARow(2, b, player) +
                100 * numInARow(3, b, player);
    }

    private int numInARow(int count, Board b, char player) {
        int consecutiveTokens = 0;
        char[][] thisBoard = b.getBoard();
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                if (thisBoard[row][col] == player) {

                    // ----- Vertical Checks -------
                    boolean isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (row + x >= 6 || thisBoard[row + x][col] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if ( (row + count < 6 && thisBoard[row + count][col] == player) ||
                    (row - 1 >= 0 && thisBoard[row - 1][col] == player) ) {
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        // System.out.println("Found vertical in row:" + row + " col:" + col);
                        consecutiveTokens++;
                    }
                    // ----- Horizontal Checks -------
                    isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (col + x >= 7 || thisBoard[row][col + x] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if ( (col + count < 7 && thisBoard[row][col + count] == player) || (col - 1 >= 0 && thisBoard[row][col - 1] == player) ) {
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        // System.out.println("Found horizontal in row:" + row + " col:" + col);
                        consecutiveTokens++;
                    }
                    // ----- Diagonal Down Checks -------
                    isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (row + x >= 6 || col + x >= 7 || thisBoard[row + x][col + x] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if ( (row + count < 6 && col + count < 7 && thisBoard[row + count][col + count] == player) ||
                    (row - 1 >= 0 && col - 1 >= 0 && thisBoard[row - 1][col - 1] == player) ){
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        // System.out.println("Found diagonal down in row:" + row + " col:" + col);
                        consecutiveTokens++;
                    }
                    // ----- Diagonal Up Checks -------
                    isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (row - x < 0 || col + x >= 7 || thisBoard[row - x][col + x] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if ((row - count >= 0 && col + count < 7 && thisBoard[row - count][col + count] == player) ||
                        (row + 1 < 6 && col - 1 >= 0 && thisBoard[row + 1][col - 1] == player) ){
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        // System.out.println("Found diagonal up in row:" + row + " col:" + col);
                        consecutiveTokens++;
                    }
                }
            }
        }
        return consecutiveTokens;
    }

}
