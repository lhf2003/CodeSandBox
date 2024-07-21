import java.util.ArrayList;

public class Main{
    public static void main(String[] args) {
        // 制造内存溢出
        ArrayList<byte[]> list = new ArrayList<>();
        while (true){
            list.add(new byte[10000]);
        }
    }
}