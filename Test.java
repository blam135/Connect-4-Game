public class Test {
    public static void main(String[] args) {
        /*
        Test:
        1) ..yyrrr,..ryryr,....y..,.......,.......,.......
        2) .ryyrry,.rryry.,..y.r..,..y....,.......,.......
        3) yyryry.,rryrry.,.rryrr.,.yyryy.,.yyr.r.,.......
        
        javac *.java && java Test
        */        
        Board b = new Board("yyryry.,rryrry.,.rryrr.,.yyryy.,.yyr.r.,.......", 'M', 2);
        b.printBoard();
        System.out.println(b.numInARow(2, 'r'));
        System.out.println(b.numInARow(3, 'r'));
        System.out.println(b.numInARow(2, 'y'));
        System.out.println(b.numInARow(3, 'y'));
    }
}