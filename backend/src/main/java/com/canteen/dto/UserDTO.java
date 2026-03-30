package com.canteen.dto;

import lombok.Setter;
import lombok.Getter;

@Getter
@Setter
public class UserDTO {
    private String name;
    private Integer age;

    @Override
    public String toString() {
        return "UserDTO{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
