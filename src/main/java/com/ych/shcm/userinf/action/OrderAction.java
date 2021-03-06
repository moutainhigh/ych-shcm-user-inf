package com.ych.shcm.userinf.action;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ych.core.model.CommonOperationResult;
import com.ych.core.model.CommonOperationResultWidthData;
import com.ych.core.model.PagedList;
import com.ych.shcm.o2o.action.UserAction;
import com.ych.shcm.o2o.annotation.JWTAuth;
import com.ych.shcm.o2o.model.*;
import com.ych.shcm.o2o.parameter.QueryOrderAppointmentListParameter;
import com.ych.shcm.o2o.parameter.QueryOrderListParameter;
import com.ych.shcm.o2o.service.*;

/**
 * 订单接口
 */
@Controller("shcm.useraction.OrderAction")
public class OrderAction extends UserAction {

    @Autowired
    OrderService orderService;

    @Autowired
    private CarService carService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private CarModelService carModelService;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private MessageSource messageSource;

    /**
     * 用户订单数量接口
     *
     * @return
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @ResponseBody
    @RequestMapping("wxmp/order/myOrderStatusCount")
    public CommonOperationResultWidthData queryOrderNumGroupByType() {

        CommonOperationResultWidthData<List<OrderStatusCount>> ret = new CommonOperationResultWidthData<>();
        ret.setData(orderService.getOrderCountGroupByType(getUser().getId()));
        ret.setResult(CommonOperationResult.Succeeded);
        return ret;
    }

    /**
     * 创建订单
     *
     * @param createOrderRequest
     *         创建订单的请求
     * @return 结果
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @ResponseBody
    @RequestMapping("wxmp/order/createOrder")
    public CommonOperationResultWidthData<Map<String, Object>> createOrder(@RequestBody CreateOrderRequestBody createOrderRequest) {
        CommonOperationResultWidthData<Map<String, Object>> ret = new CommonOperationResultWidthData<>();
        User user = getUser();
        try {
            Assert.notNull(createOrderRequest.getCarId(), messageSource.getMessage("car.id.required", null, Locale.getDefault()));
            Assert.notEmpty(createOrderRequest.getPacks(), messageSource.getMessage("servicePack.required", null, Locale.getDefault()));
        } catch (IllegalArgumentException e) {
            ret.setResult(CommonOperationResult.IllegalArguments);
            ret.setDescription(e.getMessage());
            return ret;
        }
        Order order = new Order();
        order.setAccessChannelId(BigDecimal.ONE);
        order.setUserId(user.getId());
        order.setCarId(createOrderRequest.getCarId());
        List<OrderServicePack> orderServicePacks = new ArrayList<>();
        for (ServicePack servicePack : createOrderRequest.getPacks()) {
            OrderServicePack orderServicePack = new OrderServicePack();
            orderServicePacks.add(orderServicePack);
            orderServicePack.setServicePackId(servicePack.getId());
        }
        order.setOrderServicePacks(orderServicePacks);
        return orderService.createOrder(order);
    }

    /**
     * 订单创建请求体
     */
    public static class CreateOrderRequestBody {

        BigDecimal carId;

        List<ServicePack> packs;

        /**
         * Getter for property 'carId'.
         *
         * @return Value for property 'carId'.
         */
        public BigDecimal getCarId() {
            return carId;
        }

        /**
         * Setter for property 'carId'.
         *
         * @param carId
         *         Value to set for property 'carId'.
         */
        public void setCarId(BigDecimal carId) {
            this.carId = carId;
        }

        /**
         * Getter for property 'packs'.
         *
         * @return Value for property 'packs'.
         */
        public List<ServicePack> getPacks() {
            return packs;
        }

        /**
         * Setter for property 'packs'.
         *
         * @param packs
         *         Value to set for property 'packs'.
         */
        public void setPacks(List<ServicePack> packs) {
            this.packs = packs;
        }
    }

    /**
     * 分页获取订单列表/wx端保养维修记录
     *
     * @param parameter
     *         查询条件
     * @return 分页列表
     */
    @JWTAuth(issuer = {Constants.WEB_CONSOLE_OPERATOR_ISSUER, Constants.WECHAT_MP_ISSUER})
    @ResponseBody
    @RequestMapping({"console/order/queryList", "wxmp/order/queryList", "sone/order/queryList"})
    public CommonOperationResultWidthData<PagedList<Order>> queryOrderList(@RequestBody QueryOrderListParameter parameter, HttpServletRequest request) {
        CommonOperationResultWidthData<PagedList<Order>> ret = new CommonOperationResultWidthData<>();
        if (request.getServletPath().contains("wxmp/order/queryList")) {
            parameter.setNeedPacks(true);
            ret.setData(orderService.getOrderPageList(parameter));
        } else if (request.getServletPath().contains("sone/order/queryList")) {
            List<Shop> shops = shopService.getByUserId(getUser().getId());
            if (CollectionUtils.isEmpty(shops)) {
                ret.setResult(CommonOperationResult.IllegalOperation);
                ret.setDescription(messageSource.getMessage("user.shop.notBinded", null, Locale.getDefault()));
                return ret;
            }
            parameter.setShopId(shops.get(0).getId());
            ret.setData(orderService.getOrderPageList(parameter));
            for (Order order : ret.getData().getList()) {
                order.setCar(carService.getCarById(order.getCarId()));
                order.getCar().setCarModel(carModelService.getModelById(order.getCar().getModelId()));
                order.getCar().getCarModel().setSeries(carModelService.getSeriesById(order.getCar().getCarModel().getSeriesId()));
                order.getCar().getCarModel().getSeries().setCarBrand(carModelService.getBrandById(order.getCar().getCarModel().getSeries().getBrandId()));
                order.getCar().getCarModel().getSeries().setCarFactory(carModelService.getFactoryById(order.getCar().getCarModel().getSeries().getFactoryId()));
            }
        } else {
            ret.setResult(CommonOperationResult.Succeeded);
            ret.setData(orderService.getOrderPageList(parameter));
        }
        ret.setResult(CommonOperationResult.Succeeded);
        return ret;
    }

    /**
     * 取得订单详情
     *
     * @param orderId
     *         订单ID
     * @return 分页列表
     */
    @JWTAuth(issuer = {Constants.WEB_CONSOLE_OPERATOR_ISSUER, Constants.WECHAT_MP_ISSUER})
    @ResponseBody
    @RequestMapping({"console/order/orderDetail", "wxmp/order/orderDetail", "sone/order/orderDetail"})
    public CommonOperationResultWidthData<Order> orderDetail(@RequestParam BigDecimal orderId) {
        CommonOperationResultWidthData<Order> ret = new CommonOperationResultWidthData<>();
        if (orderId == null) {
            ret.setResult(CommonOperationResult.IllegalArguments);
            ret.setDescription(messageSource.getMessage("order.id.required", null, Locale.getDefault()));
            return ret;
        }
        Order order = orderService.getById(orderId);
        if (order == null) {
            ret.setResult(CommonOperationResult.NotExists);
            ret.setDescription(messageSource.getMessage("order.id.notExists", null, Locale.getDefault()));
            return ret;
        }

        ret.setData(order);
        ret.setResult(CommonOperationResult.Succeeded);
        return ret;
    }

    /**
     * 查询订单发票信息
     *
     * @param orderId
     *         订单id
     * @return 结果
     */
    @JWTAuth(issuer = {Constants.WEB_CONSOLE_OPERATOR_ISSUER, Constants.WECHAT_MP_ISSUER})
    @RequestMapping("order/queryOrderBill")
    @ResponseBody
    public CommonOperationResultWidthData<OrderBill> queryOrderBillByOrderId(@RequestParam BigDecimal orderId) {
        CommonOperationResultWidthData<OrderBill> ret = new CommonOperationResultWidthData<>();
        try {
            Assert.notNull(orderId, messageSource.getMessage("order.id.required", null, Locale.getDefault()));
        } catch (IllegalArgumentException e) {
            ret.setResult(CommonOperationResult.IllegalArguments);
            ret.setDescription(e.getMessage());
            return ret;
        }
        OrderBill orderBill = orderService.getOrderBillByOrderId(orderId);
        ret.setData(orderBill);
        if (orderBill == null) {
            ret.setResult(CommonOperationResult.NotExists);
            ret.setDescription(messageSource.getMessage("order.bill.notExists", null, Locale.getDefault()));
        } else {
            ret.setResult(CommonOperationResult.Succeeded);
        }
        return ret;
    }

    /**
     * 查询评价信息
     *
     * @param orderId
     *         订单id
     * @return 结果
     */
    @JWTAuth(issuer = {Constants.WEB_CONSOLE_OPERATOR_ISSUER, Constants.WECHAT_MP_ISSUER})
    @RequestMapping("order/queryOrderEvaluation")
    @ResponseBody
    public CommonOperationResultWidthData<OrderEvaluation> queryOrderEvaluationByOrderId(@RequestParam("orderId") BigDecimal orderId) {
        CommonOperationResultWidthData<OrderEvaluation> ret = new CommonOperationResultWidthData<>();
        ret.setData(orderService.getOrderEvaluationByOrderId(orderId));
        ret.setResult(CommonOperationResult.Succeeded);
        return ret;
    }

    /**
     * 取得订单下的服务
     *
     * @param orderId
     *         订单Id
     * @param needItems
     *         是否需要填充orderServiceItem
     * @return 服务项目
     */
    @JWTAuth(issuer = {Constants.WEB_CONSOLE_OPERATOR_ISSUER, Constants.WECHAT_MP_ISSUER})
    @RequestMapping("order/queryServicePack")
    @ResponseBody
    public CommonOperationResultWidthData<List<OrderServicePack>> queryServicePackByOrderId(@RequestParam BigDecimal orderId, @RequestParam boolean needItems) {
        CommonOperationResultWidthData<List<OrderServicePack>> ret = new CommonOperationResultWidthData<>();
        ret.setData(orderService.getPackByOrderId(orderId, needItems));
        for (OrderServicePack orderServicePack : ret.getData()) {
            for (OrderServicePackItem item : orderServicePack.getOrderServicePackItems()) {
                item.setIconPath(uploadService.getFileUrl(item.getIconPath()));
            }
        }
        ret.setResult(CommonOperationResult.Succeeded);
        return ret;
    }

    /**
     * 取消订单
     *
     * @param orderId
     *         订单Id
     * @return 结果
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @RequestMapping("wxmp/order/cancel")
    @ResponseBody
    public CommonOperationResultWidthData cancelOrder(@RequestParam BigDecimal orderId) {
        return orderService.cancelOrder(orderId);

    }

    /**
     * 确认服务
     *
     * @param orderId
     *         订单Id
     * @return 结果
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @RequestMapping("wxmp/order/confirmService")
    @ResponseBody
    public CommonOperationResultWidthData confirmService(@RequestParam BigDecimal orderId) {
        return orderService.confirmService(orderId);
    }

    /**
     * 申请开票
     *
     * @param orderBill
     *         开票申请
     * @return 结果
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @RequestMapping("wxmp/order/applyBill")
    @ResponseBody
    public CommonOperationResultWidthData applyBill(@RequestBody OrderBill orderBill) {
        CommonOperationResultWidthData ret = new CommonOperationResultWidthData();
        try {
            Assert.notNull(orderBill, messageSource.getMessage("order.bill.required", null, Locale.getDefault()));
            Assert.notNull(orderBill.getOrderId(), messageSource.getMessage("order.id.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getBank(), messageSource.getMessage("company.bank.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getBankAccount(), messageSource.getMessage("company.bankAccount.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getCompany(), messageSource.getMessage("company.name.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getCompanyAddr(), messageSource.getMessage("company.address.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getDeliverAddr(), messageSource.getMessage("order.bill.deliverAddr.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getTaxNo(), messageSource.getMessage("order.bill.taxNo.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getPtc(), messageSource.getMessage("order.bill.ptc.required", null, Locale.getDefault()));
            Assert.hasLength(orderBill.getPhone(), messageSource.getMessage("company.phone.required", null, Locale.getDefault()));
        } catch (IllegalArgumentException e) {
            ret.setResult(CommonOperationResult.IllegalArguments);
            ret.setDescription(e.getMessage());
            return ret;
        }
        return orderService.addOrderBill(orderBill);
    }

    /**
     * 评价
     *
     * @param orderEvaluation
     *         评价信息
     * @return 结果
     * 订单Id
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @RequestMapping({"wxmp/order/evaluateOrder"})
    @ResponseBody
    public CommonOperationResultWidthData evaluateOrder(@RequestBody OrderEvaluation orderEvaluation) {
        CommonOperationResultWidthData ret = new CommonOperationResultWidthData();
        try {
            Assert.notNull(orderEvaluation, messageSource.getMessage("order.evaluation.required", null, Locale.getDefault()));
            Assert.notNull(orderEvaluation.getOrderId(), messageSource.getMessage("order.id.required", null, Locale.getDefault()));
            Assert.notNull(orderEvaluation.getSkill(), messageSource.getMessage("order.evaluation.skill.required", null, Locale.getDefault()));
            Assert.notNull(orderEvaluation.getAttitude(), messageSource.getMessage("order.evaluation.attitude.required", null, Locale.getDefault()));
            Assert.notNull(orderEvaluation.getEfficiency(), messageSource.getMessage("order.evaluation.efficiency.required", null, Locale.getDefault()));
            Assert.notNull(orderEvaluation.getEnvironment(), messageSource.getMessage("order.evaluation.environment.required", null, Locale.getDefault()));
        } catch (IllegalArgumentException e) {
            ret.setResult(CommonOperationResult.IllegalArguments);
            ret.setDescription(e.getMessage());
            return ret;
        }
//        orderEvaluation.setAverage(orderEvaluation.getAttitude().add(orderEvaluation.getEfficiency()).add(orderEvaluation.getEnvironment()).divide(new BigDecimal(3),1,BigDecimal.ROUND_HALF_UP));
        orderEvaluation.setAverage(new BigDecimal(orderEvaluation.getAttitude().add(orderEvaluation.getEfficiency()).add(orderEvaluation.getEnvironment()).add(orderEvaluation.getSkill()).doubleValue() / 4));

        return orderService.evaluateOrder(orderEvaluation);
    }

    /**
     * 门店预约
     *
     * @param orderAppointment
     *         预约实体
     * @return 结果
     */
    @JWTAuth(issuer = Constants.WECHAT_MP_ISSUER)
    @RequestMapping("wxmp/order/appointmentOrder")
    @ResponseBody
    public CommonOperationResultWidthData appointmentOrder(@RequestBody OrderAppointment orderAppointment) {
        return orderService.addAppointment(orderAppointment);
    }

    /**
     * 查询订单预约
     *
     * @param parameter
     *         查询条件
     * @return 结果
     */
    @JWTAuth(Constants.WECHAT_MP_ISSUER)
    @RequestMapping({"wxmp/order/queryOrderAppointmentsOfShop", "wxmp/order/queryOrderAppointmentsOfUser"})
    @ResponseBody
    public CommonOperationResultWidthData<PagedList<OrderAppointment>> queryOrderAppointments(HttpServletRequest request, @RequestBody QueryOrderAppointmentListParameter parameter) {
        CommonOperationResultWidthData<PagedList<OrderAppointment>> ret = new CommonOperationResultWidthData<>();

        if (request.getServletPath().contains("wxmp/order/queryOrderAppointmentsOfUser")) {
            parameter.setUserId(getUser().getId());
        }
        ret.setData(orderService.getAppointmentList(parameter));
        ret.setResult(CommonOperationResult.Succeeded);
        if (parameter.isNeedOrderInfo()) {
            for (OrderAppointment orderAppointment : ret.getData().getList()) {
                orderAppointment.getOrder().setCar(carService.getCarById(orderAppointment.getOrder().getCarId()));
                orderAppointment.getOrder().getCar().setCarModel(carModelService.getModelById(orderAppointment.getOrder().getCar().getModelId()));
                orderAppointment.getOrder().getCar().getCarModel().setSeries(carModelService.getSeriesById(orderAppointment.getOrder().getCar().getCarModel().getSeriesId()));
                orderAppointment.getOrder().getCar().getCarModel().getSeries().setCarBrand(carModelService.getBrandById(orderAppointment.getOrder().getCar().getCarModel().getSeries().getBrandId()));
                orderAppointment.getOrder().getCar().getCarModel().getSeries().setCarFactory(carModelService.getFactoryById(orderAppointment.getOrder().getCar().getCarModel().getSeries().getFactoryId()));
            }
        }
        if (request.getServletPath().contains("wxmp/order/queryOrderAppointmentsOfUser")) {
            for (OrderAppointment orderAppointment : ret.getData().getList()) {
                orderAppointment.setShop(shopService.getShopById(orderAppointment.getShopId()));
            }
        }
        return ret;
    }

    /**
     * 查询门店订单预约详情
     *
     * @param id
     *         预约id
     * @return 结果
     */
    @JWTAuth(Constants.WECHAT_MP_ISSUER)
    @RequestMapping("wxmp/order/orderAppointmentsDetail")
    @ResponseBody
    public CommonOperationResultWidthData<OrderAppointment> queryOrderAppointmentsDetail(@RequestParam BigDecimal id) {
        CommonOperationResultWidthData<OrderAppointment> ret = new CommonOperationResultWidthData<>();
        ret.setResult(CommonOperationResult.Succeeded);
        ret.setData(orderService.getOrderAppointmentById(id));
        return ret;
    }
}
