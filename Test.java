public class Test {
    public static void main(String[] args) {
        /*
        Test:
        ..yyrrr,..ryryr,....y..,.......,.......,.......
        .ryyrry,.rryry.,..y.r..,..y....,.......,.......
        javac *.java && java Test
        */        
        Board b = new Board("..yyrrr,..ryryr,....y..,.......,.......,.......", 'M', 2);
        System.out.println(b.evaluation());
    }
}