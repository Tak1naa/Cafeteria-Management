package com.canteen;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Address {
    private String province;
    private String city;

    @Override
    public String toString() {
        return "'" + province + '\'' +
                "，'" + city + '\'' ;
    }
}
