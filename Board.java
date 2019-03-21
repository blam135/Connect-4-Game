public class Board {

    private char[][] gameState; 
    private int redTokenCount = 21;
    private int yellowTokenCount = 21;
    private char algorithmType;
    private int maxDepth;

    public Board(String gameStateString, char algorithmType, int maxDepth) {
        gameState = new char[6][7];
        gameStateString = gameStateString.replaceAll(",", "");
        int stringCounter = 0;
        for (int row = gameState.length - 1; row >= 0; row--) {
            for (int col = 0; col < gameState[row].length; col++) {
                gameState[row][col] = gameStateString.charAt(stringCounter);
                stringCounter++;

                if (gameState[row][col] == 'r') {
                    redTokenCount--;
                } else if (gameState[row][col] == 'y') {
                    yellowTokenCount--;
                }
            }
        }

        this.algorithmType = algorithmType;
        this.maxDepth = maxDepth;
    }

    public void printBoard() {        
        for (int i = 0; i < gameState.length; i++) {
            for (int j = 0; j < gameState[i].length; j++) {
                System.out.print(gameState[i][j]);
            }
            System.out.println();
        }
    }

    public int getTokenCountYellow() {
        return yellowTokenCount;
    }

    public int getTokenCountRed() {
        return redTokenCount;
    }

    public int evaluation() {
        return score('r') - score('y');
    }

    public int score(char player) {
        int tokensOnBoard;

        if (player == 'r') {
            tokensOnBoard = 21 - redTokenCount;
        } else {
            tokensOnBoard = 21 - yellowTokenCount;
        }

        return (tokensOnBoard) + 
        (10 * numInARow(2, player)) + 
        (100 * numInARow(3, player)) +
        (1000 * numInARow(4, player));
    }

    public int numInARow(int count, char player) {
        int consecutiveTokens = 0;
        for (int row = 0; row < gameState.length; row++) {
            for (int col = 0; col < gameState[row].length; col++) {
                if (gameState[row][col] == player) {

                    // ----- Vertical Checks ------- 
                    boolean isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (row + x >= gameState.length) {
                            isConsecutive = false;
                            break;
                        } 
                        if (gameState[row + x][col] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if (row + count < gameState.length &&
                    gameState[row + count][col] == player) {
                        isConsecutive = false;
                    } else if (row - 1 >= 0 &&
                    gameState[row - 1][col] == player) {
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        System.out.println("Found vertical in row:" + row + " col:" + col);
                        consecutiveTokens++;                        
                    }

                    // ----- Horizontal Checks ------- 
                    isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (col + x >= gameState[row].length) {
                            isConsecutive = false;
                            break;
                        } 
                        if (gameState[row][col + x] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if (col + count < gameState.length &&
                    gameState[row][col + count] == player) {
                        isConsecutive = false;
                    } else if (col - 1 >= 0 &&
                    gameState[row][col - 1] == player) {
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        System.out.println("Found horizontal in row:" + row + " col:" + col);
                        consecutiveTokens++;                        
                    }

                    // ----- Diagonal Down Checks ------- 
                    isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (row + x >= gameState.length ||
                            col + x >= gameState[row].length) {
                                isConsecutive = false;
                                break;
                        } 
                        if (gameState[row + x][col + x] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if (row + count < gameState.length &&
                    col + count < gameState[row].length && 
                    gameState[row + count][col + count] == player) {
                        isConsecutive = false;
                    } else if (row - 1 <= 0 &&
                    col - 1 <= 0 &&
                    gameState[row - 1][col - 1] == player) {
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        System.out.println("Found diagonal down in row:" + row + " col:" + col);
                        consecutiveTokens++;                        
                    }

                    // ----- Diagonal Up Checks ------- 
                    isConsecutive = true;
                    for (int x = 0; x < count; x++) {
                        if (row - x < 0 ||
                            col + x >= gameState.length ) {
                                isConsecutive = false;
                                break;
                        } 
                        if (gameState[row - x][col + x] != player) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    if (row - count >= 0 &&
                    col + count < gameState[row].length && 
                    gameState[row - count][col + count] == player) {
                        isConsecutive = false;
                    } else if (row + 1 < gameState.length &&
                    col - 1 >= 0 &&
                    gameState[row + 1][col - 1] == player) {
                        isConsecutive = false;
                    }
                    if (isConsecutive) {
                        System.out.println("Found diagonal up in row:" + row + " col:" + col);
                        consecutiveTokens++;                        
                    }

                }
            }
        }
        return consecutiveTokens;
    }

    public char getAlgorithmType() {
        return algorithmType;
    }

}