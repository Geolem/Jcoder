package org.nlpcn.jcoder.controller;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.nlpcn.jcoder.domain.ClassDoc;
import org.nlpcn.jcoder.domain.Group;
import org.nlpcn.jcoder.domain.Task;
import org.nlpcn.jcoder.filter.AuthoritiesManager;
import org.nlpcn.jcoder.run.java.JavaRunner;
import org.nlpcn.jcoder.scheduler.ThreadManager;
import org.nlpcn.jcoder.service.TaskService;
import org.nlpcn.jcoder.util.ExceptionUtil;
import org.nlpcn.jcoder.util.JavaDocUtil;
import org.nlpcn.jcoder.util.StaticValue;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.mvc.Mvcs;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.By;
import org.nutz.mvc.annotation.Filters;
import org.nutz.mvc.annotation.Ok;
import org.nutz.mvc.annotation.Param;

import com.alibaba.fastjson.JSONObject;

@IocBean
public class ApiAction {

	private static final Logger LOG = Logger.getLogger(ApiAction.class);

	/**
	 * api
	 * 
	 * @return api info
	 */
	@At("/api")
	@Ok("json")
	public Object api() {
		Collection<Task> list = TaskService.findTaskList(1);

		Map<Long, List<Task>> collect = list.stream().collect(Collectors.groupingBy(t -> t.getGroupId()));

		Map<Long, String> groupMap = new HashMap<>();

		List<Group> groups = StaticValue.systemDao.search(Group.class, "id");

		groups.forEach(g -> groupMap.put(g.getId(), g.getName()));

		Map<String, List<ClassDoc>> apiInfo = new HashMap<>();

		collect.forEach((k, v) -> {
			apiInfo.put(groupMap.get(k), list.stream().map((t) -> {
				boolean compile = false;

				ClassDoc cd = null;
				try {
					new JavaRunner(t).compile();
					compile = true;
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				try {
					cd = JavaDocUtil.parse(new StringReader(t.getCode()));
				} catch (Exception e) {
					cd = new ClassDoc(t.getName());
				}

				cd.setStatus(compile);

				return cd;

			}).collect(Collectors.toList()));
		});

		return apiInfo;

	}

	/**
	 * 执行测试用户的api
	 * 
	 * @param jsonTask
	 * @return
	 */
	@At("/run_api")
	@Ok("raw")
	@Filters(@By(type = AuthoritiesManager.class, args = { "userType", "1", "/login.jsp" }))
	public Object runApi(@Param("json") String jsonTask) {

		String taskName = null;
		try {

			JSONObject json = JSONObject.parseObject(jsonTask);

			String code = json.getString("task.code");
			String codeType = json.getString("task.codeType");

			Task task = new Task();
			task.setCode(code);
			task.setCodeType(codeType);
			task.setType(2);

			taskName = task.getName() + "@" + "0@" + Mvcs.getReq().getRemoteAddr();

			if (ThreadManager.checkActionExists(taskName)) {
				LOG.warn(taskName + " has beening run! pleast stop it first!");
			} else {
				LOG.info(taskName + " publish ok ! will be run !");
				ThreadManager.add2ActionTask(taskName, Thread.currentThread());
				LOG.info(taskName + " result : " + new JavaRunner(task).compile().instanceObjByIoc().execute());
			}

		} catch (Exception e) {
			e.printStackTrace();
			LOG.error(taskName + " " + ExceptionUtil.printStackTraceWithOutLine(e));

			return StaticValue.errMessage(e.getMessage());
		} finally {
			if (taskName != null)
				ThreadManager.removeActionIfOver(taskName);
		}

		return StaticValue.okMessage("code run over!");
	}

	/**
	 * 停止一个api任务
	 * 
	 * @param jsonTask
	 * @return
	 */
	@At("/stop_api")
	@Ok("raw")
	@Filters(@By(type = AuthoritiesManager.class, args = { "userType", "1", "/login.jsp" }))
	public Object stopApi(@Param("json") String jsonTask) {

		String taskName = null;
		try {
			LOG.info(taskName + " will be to stop ! ");
			JSONObject json = JSONObject.parseObject(jsonTask);
			taskName = json.getString("task.name") + "@" + "0@" + Mvcs.getReq().getRemoteAddr();
			ThreadManager.stop(taskName);
			LOG.info(taskName + " stoped ! ");
			return StaticValue.OK;
		} catch (Exception e) {
			e.printStackTrace();
			LOG.error(taskName + " err " + ExceptionUtil.printStackTraceWithOutLine(e));
			return StaticValue.ERR;
		}
	}

}
