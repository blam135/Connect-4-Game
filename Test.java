public class Test {
    public static void main(String[] args) {
        /*
        Test:
        1) ..yyrrr,..ryryr,....y..,.......,.......,.......
        2) .ryyrry,.rryry.,..y.r..,..y....,.......,.......
        3) yyryry.,rryrry.,.rryrr.,.yyryy.,.yyr.r.,.......
        4) yryryyr,yryyrry,.yrrryy,..y.yry,..r.rrr,..y..r.
        javac *.java && java Test
        */        
        Board b = new Board("yryryyr,yryyrry,.yrrryy,..y.yry,..r.rrr,..y..r.", 'M', 2);
        
        // b.printBoard();

        /*
        System.out.println(b.evaluation());
        System.out.println(b.score('y')); 
        System.out.println(b.score('r')); 
        */

        /*
        System.out.println("Num In A Row for red with count 2 -> " + b.numInARow(2, 'r'));
        System.out.println("Num In A Row for red with count 3 -> " + b.numInARow(3, 'r'));
        System.out.println("Num In A Row for yellow with count 2 -> " + b.numInARow(2, 'y')); 
        System.out.println("Num In A Row for yellow with count 3 -> " + b.numInARow(3, 'y')); 
        */
    }
}