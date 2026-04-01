package com.canteen.controller;

import com.canteen.dto.UserDTO;
import com.canteen.pojo.User;
import com.canteen.pojo.UserList;
import com.canteen.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/hello")
    public String hello() {
        log.info("访问 /hello 接口");
        String result = "Programme successfully launched @" + LocalDateTime.now();
        log.info("返回结果: {}", result);
        return result;
    }

    @GetMapping("/world")
    public String world(){
        log.info("访问 /world 接口");
        String result = "hello world!";
        log.debug("返回结果: {}", result);
        return result;
    }

    @RequestMapping(value = "/simpleParam", method = {RequestMethod.GET, RequestMethod.POST})
    public String simpleParam(@RequestParam String[] name,
                              @RequestParam Integer[] age){
        log.info("访问 /simpleParam 接口， 参数 name: {}, age: {}",
                Arrays.toString(name),Arrays.toString(age));

        log.debug("详细参数 - name: {}, age: {}",
                Arrays.toString(name), Arrays.toString(age));

        return "OK";
    }

    @PostMapping("/userDTO")
    public String handleUserDto(@RequestBody UserDTO userDTO){
        System.out.println(userDTO);
        log.info("接收到用户DTO：{}", userDTO);
        return "OK";
    }

    @RequestMapping("/simplePojo")
    public String simplePojo(@RequestBody UserList userList){
        log.info("访问 /simplePojo 接口，接收到的用户列表大小: {}",
                userList.getUsers() != null ? userList.getUsers().size() : 0);
        for (User user : userList.getUsers()){
            log.debug("用户信息: {}", user);
        }
        return "OK";
    }

    @GetMapping("/dateparam/{updateTime}")
    public String dateParam(
            @PathVariable
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime updateTime){

        log.info("访问 /dateparam 接口，原始时间参数:{}", updateTime);

        String formattedTime = DateUtils.format(updateTime);

        log.info("格式化后的时间: {}", formattedTime);
        log.debug("原始值: {}, 格式化后: {}", updateTime, formattedTime);

        return formattedTime;
    }

    @RequestMapping("/path/{id}")
    public String pathParam(@PathVariable Integer id){
        log.info("访问 /path/{} 接口", id);
        log.debug("接收到的路径参数 ID: {}", id);
        return "OK";
    }
}