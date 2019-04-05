public class ConnectFour {
    public static void main(String[] args) {
        String gameStateString = args[0];
        String colour = args[1]; 
        String alphaBeta = args[2];
        int searchDepth = Integer.parseInt(args[3]);
        
        Computer computer = new Computer();
        Board b = new Board(gameStateString);

        int[] result = computer.computeColumn(alphaBeta.charAt(0) == 'A', b, searchDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, colour.charAt(0) == 'r');

        System.out.println(result[0]);
        System.out.println(computer.getNodesTraversed());
    }

}