package com.imooc.miaosha.controller;

import com.imooc.miaosha.access.AccessLimit;
import com.imooc.miaosha.rabbitmq.MQSender;
import com.imooc.miaosha.rabbitmq.MiaoshaMessage;
import com.imooc.miaosha.redis.GoodsKey;
import com.imooc.miaosha.redis.MiaoshaKey;
import com.imooc.miaosha.redis.OrderKey;
import com.imooc.miaosha.result.Result;
import com.sun.org.apache.bcel.internal.classfile.Code;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import com.imooc.miaosha.domain.MiaoshaOrder;
import com.imooc.miaosha.domain.MiaoshaUser;
import com.imooc.miaosha.domain.OrderInfo;
import com.imooc.miaosha.redis.RedisService;
import com.imooc.miaosha.result.CodeMsg;
import com.imooc.miaosha.service.GoodsService;
import com.imooc.miaosha.service.MiaoshaService;
import com.imooc.miaosha.service.MiaoshaUserService;
import com.imooc.miaosha.service.OrderService;
import com.imooc.miaosha.vo.GoodsVo;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/miaosha")
public class MiaoshaController implements InitializingBean {

	@Autowired
	MiaoshaUserService userService;
	
	@Autowired
	RedisService redisService;
	
	@Autowired
	GoodsService goodsService;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	MiaoshaService miaoshaService;

	@Autowired
	MQSender sender;

	//内存标记
	private HashMap<Long, Boolean> localOverMap =  new HashMap<Long, Boolean>();
	@Override
	/**
	 * 系统初始化时执行
	 */
	public void afterPropertiesSet() throws Exception {

		//查询出商品 做预减操作，把数据库的库存查出放入Redis中
		List<GoodsVo> goodsVoList = goodsService.listGoodsVo();
        if (CollectionUtils.isEmpty(goodsVoList)){
        	return;
		}
        for (GoodsVo good : goodsVoList){
        	//放入redis
			localOverMap.put(good.getId(),false);
			redisService.set(GoodsKey.getMiaoshaGoodsStock,""+good.getId(),good.getStockCount());
		}
	}
	
//    @RequestMapping("/do_miaosha")
//    public String list(Model model,MiaoshaUser user,
//    		@RequestParam("goodsId")long goodsId) {
//    	model.addAttribute("user", user);
//    	if(user == null) {
//    		return "login";
//    	}
//    	//判断库存
//     	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
//    	int stock = goods.getStockCount();
//    	if(stock <= 0) {
//    		model.addAttribute("errmsg", CodeMsg.MIAO_SHA_OVER.getMsg());
//    		return "miaosha_fail";
//    	}
//    	//判断是否已经秒杀到了
//    	MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
//    	if(order != null) {
//    		model.addAttribute("errmsg", CodeMsg.REPEATE_MIAOSHA.getMsg());
//    		return "miaosha_fail";
//    	}
//    	//减库存 下订单 写入秒杀订单
//    	OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
//    	model.addAttribute("orderInfo", orderInfo);
//    	model.addAttribute("goods", goods);
//        return "order_detail";
//    }

	@PostMapping("/{path}/do_miaosha")
	public Result<Integer> list(MiaoshaUser user,
					   @RequestParam("goodsId")long goodsId,@PathVariable("path")String path) {
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
        //验证path
		boolean check = miaoshaService.checkPath(user,goodsId,path);
		if (!check){
			return Result.error(CodeMsg.REQUEST_ILLEGAL);
		}
		//内存标记，减少redis访问
		boolean over = localOverMap.get(goodsId);
		if(over) {
			return Result.error(CodeMsg.MIAO_SHA_OVER);
		}

		//减redis中的库存
		Long stock = redisService.decr(GoodsKey.getMiaoshaGoodsStock, "" + goodsId);
		if (stock < 0){
			//减空了
			localOverMap.put(goodsId,true);
			return Result.error(CodeMsg.MIAO_SHA_OVER);
		}
        //判断是否已经秒杀到了
		MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(),goodsId);
		if (order != null){
			return Result.error(CodeMsg.REPEATE_MIAOSHA);
		}
		//发送消息给队列 入队操作
		MiaoshaMessage mm  = new MiaoshaMessage();

		mm.setGoodsId(goodsId);
		mm.setUser(user);
		sender.sendMiaoshaMessage(mm);
		return Result.success(0);


//		//判断库存
//		GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);//10个商品，req1 req2
//		int stock = goods.getStockCount();
//		if(stock <= 0) {
//			return Result.error(CodeMsg.MIAO_SHA_OVER);
//		}
//		//判断是否已经秒杀到了
//		MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
//		if(order != null) {
//			return Result.error(CodeMsg.REPEATE_MIAOSHA);
//		}
//		//减库存 下订单 写入秒杀订单
//		OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
//		if (orderInfo == null){
//			return Result.error(CodeMsg.MIAO_SHA_OVER);
//		}
//		return Result.success(orderInfo);
	}
	/**
	 * orderId：成功
	 * -1：秒杀失败
	 * 0： 排队中
	 * */
	@GetMapping("result")
	public Result<Long> miaoshaResult(Model model,MiaoshaUser user,
									  @RequestParam("goodsId")long goodsId) {
		model.addAttribute("user", user);
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		long result  =miaoshaService.getMiaoshaResult(user.getId(), goodsId);
		return Result.success(result);
	}

	@RequestMapping(value="/reset", method=RequestMethod.GET)
	@ResponseBody
	public Result<Boolean> reset(Model model) {
		List<GoodsVo> goodsList = goodsService.listGoodsVo();
		for(GoodsVo goods : goodsList) {
			goods.setStockCount(10);
			redisService.set(GoodsKey.getMiaoshaGoodsStock, ""+goods.getId(), 10);
			localOverMap.put(goods.getId(), false);
		}
		redisService.delete(OrderKey.getMiaoshaOrderByUidGid);
		redisService.delete(MiaoshaKey.isGoodsOver);
		miaoshaService.reset(goodsList);
		return Result.success(true);
	}
	@AccessLimit(seconds=5, maxCount=5, needLogin=true)
	@GetMapping("/path")
	@ResponseBody
	public Result<String> getMiaoshaPath(HttpServletRequest request, MiaoshaUser user,
										 @RequestParam("goodsId")long goodsId,
										 @RequestParam(value="verifyCode", defaultValue="0")int verifyCode
	) {
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		boolean check = miaoshaService.checkVerifyCode(user, goodsId, verifyCode);
		if(!check) {
			return Result.error(CodeMsg.REQUEST_ILLEGAL);
		}
		String path  =miaoshaService.createMiaoshaPath(user, goodsId);
		return Result.success(path);
	}

	@GetMapping(value="/verifyCode")
	@ResponseBody
	public Result<String> getMiaoshaVerifyCod(HttpServletResponse response, MiaoshaUser user,
											  @RequestParam("goodsId")long goodsId) {
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		try {
			BufferedImage image  = miaoshaService.createVerifyCode(user, goodsId);
			OutputStream out = response.getOutputStream();
			ImageIO.write(image, "JPEG", out);
			out.flush();
			out.close();
			return null;
		}catch(Exception e) {
			e.printStackTrace();
			return Result.error(CodeMsg.MIAOSHA_FAIL);
		}
	}


}
