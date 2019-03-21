import java.util.*;

public class ConnectFour {
    public static void main(String[] args) {
        String gameStateString = ".......,.......,.......,.......,.......,.......";
        Board b = new Board(gameStateString);
        Computer computer = new Computer();        


        Scanner scan = new Scanner(System.in);
        System.out.print("Type 'y' then hit enter if you want to be yellow player otherwise hit enter: ");
        
        char player;
        try {
            player = scan.nextLine().charAt(0);
            if (player != 'y') {
                player = 'r';
            }
        } catch (StringIndexOutOfBoundsException e) {
            player = 'r';
        }
        char enemy = (player == 'y') ? 'r' : 'y';
        System.out.print("Type 'y' then hit enter if you want to go first otherwise hit enter: ");
        char firstPlayer; 
        try {
            firstPlayer = scan.nextLine().charAt(0);
        } catch (StringIndexOutOfBoundsException e) {
            firstPlayer = 'n';
        }

        int[] result = computer.computeColumn(true, b, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, enemy == 'r');
        while (true) {
            try {
                if (firstPlayer == 'y') {
                    b.printBoard();
                    System.out.print("Pick a column for " + player + ": ");
                    int column = Integer.parseInt(scan.nextLine());
                    b.putCounter(column - 1, player);
                    b.printBoard();
                } else {
                    result = computer.computeColumn(true, b, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, enemy == 'r');
                    if (result[0] == -1) {
                        break;
                    }
                    b.putCounter(result[0], enemy);
                }
                
                Thread.sleep(100);
    
                if (firstPlayer == 'y') {
                    result = computer.computeColumn(true, b, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, enemy == 'r');
                    if (result[0] == -1) {
                        break;
                    }
                    b.putCounter(result[0], enemy);
                } else {
                    b.printBoard();
                    System.out.print("Pick a column for " + player + ": ");
                    int column = Integer.parseInt(scan.nextLine());
                    b.putCounter(column - 1, player);
                    b.printBoard();
                }
            } catch (InterruptedException e) {
                System.out.println("Game has exited");
                return;
            }

        }


        b.printBoard();
        if (result[1] == -10000) {
            System.out.println("Yellow has won!");
        } else if (result[1] == 10000) {
            System.out.println("Red has won!");
        }
        System.out.println("Hit Enter to close");
        scan.nextLine();
        scan.close();

    }

}