package com.canteen.controller;

import com.canteen.pojo.User;
import com.canteen.pojo.UserList;
import com.canteen.utils.DateUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;

@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/hello")
    public String hello() {
        return "Programme successfully launched @" + LocalDateTime.now();
    }

    @GetMapping("/world")
    public String world(){ return "hello world!";}

    @RequestMapping(value = "/simpleParam", method = {RequestMethod.GET, RequestMethod.POST})
    public String simpleParam(@RequestParam String[] name,
                              @RequestParam Integer[] age){
        System.out.println(Arrays.toString(name) + ":" + Arrays.toString(age));
        return "OK";
    }

    @RequestMapping("/simplePojo")
    public String simplePojo(@RequestBody UserList userList){
        for (User user : userList.getUsers()){
            System.out.println(user);
        }
        return "OK";
    }

    @GetMapping("/dateparam/{updateTime}")
    public String dateParam(
            @PathVariable
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime updateTime){

        String formattedTime = DateUtils.format(updateTime);
        System.out.println("原始： " + updateTime);
        System.out.println("格式化： " + formattedTime);

        return formattedTime;
    }
}