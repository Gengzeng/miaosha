package com.imooc.miaosha.service;

import com.imooc.miaosha.dao.UserMapper;
import com.imooc.miaosha.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;


    public User queryUserById(Integer id){
       return userMapper.queryUserById(id);
    }
}
