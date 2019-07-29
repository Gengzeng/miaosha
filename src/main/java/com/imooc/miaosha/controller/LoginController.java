package com.imooc.miaosha.controller;

import com.imooc.miaosha.result.Result;
import com.imooc.miaosha.service.MiaoshaUserService;
import com.imooc.miaosha.vo.LoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@Slf4j
@Controller
@RequestMapping("login")
public class LoginController {

    @Autowired
    private MiaoshaUserService miaoshaUserService;

    @GetMapping("to_login")
    public String login(){
        return "login";
    }

    @PostMapping("do_login")
    @ResponseBody
    public Result<Boolean> doLogin(HttpServletResponse response,@Valid LoginVo loginVo){
        //log.info("登录信息：{}",loginVo);

        miaoshaUserService.login(response,loginVo);

        return Result.success(true);

    }

}
