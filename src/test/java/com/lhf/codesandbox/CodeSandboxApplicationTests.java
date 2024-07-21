package com.lhf.codesandbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

@SpringBootTest
class CodeSandboxApplicationTests {

    @Test
    void contextLoads() {
        ArrayList<Object> list = new ArrayList<>();

        list.add("1");
        list.add(2);
        list.add(3.0);

        System.out.println(list);
    }

}
