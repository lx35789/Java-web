package com.greenthumb.vo;

/** 登录 / 注册的返回体：{ token, user } */
public record LoginVO(String token, UserVO user) {
}
