package com.canteen;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class UserList {
    private List<User> users;

    @Override
    public String toString(){
        return "UserList{" + "users=" + users + "}";
    }
}
