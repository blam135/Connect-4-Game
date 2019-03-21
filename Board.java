public class Board {

    private char[][] gameState; 
    private int numberOfRows = 6;
    private int numberOfColumns = 7;

    public Board(String gameStateString) {
        gameState = new char[numberOfRows][numberOfColumns];
        gameStateString = gameStateString.replaceAll(",", "");
        int stringCounter = 0;
        for (int row = 0; row < numberOfRows; row++) {
            for (int col = 0; col < numberOfColumns; col++) {
                gameState[row][col] = gameStateString.charAt(stringCounter);
                stringCounter++;
            }
        }
    }

    public Board(char board[][]) {
        gameState = new char[6][7];
        for (int row = 0; row < numberOfRows; row++) {
            for (int col = 0; col < numberOfColumns; col++) {
                gameState[row][col] = board[row][col];
            }
        }
    }

    public boolean putCounter(int column, char player) {
        for (int row = 0; row < numberOfRows; row++) {
            if (gameState[row][column] == '.') {
                gameState[row][column] = player;
                return true;
            }
        }
        return false;
    }

    public void printBoard() {   
        System.out.println();
        for (int i = numberOfRows - 1; i >= 0; i--) {
            for (int j = 0; j < gameState[i].length; j++) {
                System.out.print(gameState[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println();
    }

    public int getTokenCountYellow() {
        int count = 0;
        for (int i = 0; i < numberOfRows; i++) {
            for (int j = 0; j < numberOfColumns; j++) {
                if (gameState[i][j] == 'y') {
                    count++;
                }
            }
        }
        return count;
    }

    public int getTokenCountRed() {
        int count = 0;
        for (int i = 0; i < numberOfRows; i++) {
            for (int j = 0; j < numberOfColumns; j++) {
                if (gameState[i][j] == 'r') {
                    count++;
                }
            }
        }
        return count;
    }

    public char[][] getBoard() {
        char[][] newBoard = new char[numberOfRows][numberOfColumns];
        for (int i = 0; i < numberOfRows; i++) {
            for (int j = 0; j < numberOfColumns; j++) {
                newBoard[i][j] = gameState[i][j]; 
            }
        }
        return newBoard;
    }

    public Board getCopy() {
        return new Board(this.getBoard());
    }

}
