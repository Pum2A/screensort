package pl.pum.screensort;

import org.springframework.boot.SpringApplication;

public class TestScreenSortApplication {

    public static void main(String[] args) {
        SpringApplication.from(ScreenSortApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
