package com.flowable.sparrow;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class HolidayRequest {
    public static void main(String[] args){

        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        ProcessEngine processEngine = cfg.buildProcessEngine();



        //现在我们已经有了流程BPMN 2.0 XML文件，下来需要将它部署(deploy)到引擎中。部署一个流程定义意味着：
        //流程引擎会将XML文件存储在数据库中，这样可以在需要的时候获取它。
        //流程定义转换为内部的、可执行的对象模型，这样使用它就可以启动流程实例。
        //将流程定义部署至Flowable引擎，需要使用RepositoryService，其可以从ProcessEngine对象获取。使用RepositoryService，可以通过XML文件的路径创建一个新的部署(Deployment)，并调用deploy()方法实际执行：

        RepositoryService repositoryService = processEngine.getRepositoryService();
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("holiday-request.bpmn20.xml")
                .deploy();

        //我们现在可以通过API查询验证流程定义已经部署在引擎中（并学习一些API）。通过RepositoryService创建的ProcessDefinitionQuery对象实现。
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        System.out.println("Found process definition : " + processDefinition.getName());



        //现在已经在流程引擎中部署了流程定义，因此可以使用这个流程定义作为“蓝图”启动流程实例。
        //要启动流程实例，需要提供一些初始化流程变量。一般来说，可以通过呈现给用户的表单，或者在流程由其他系统自动触发时通过REST API，来获取这些变量。在这个例子里，我们简化为使用java.util.Scanner类在命令行输入一些数据：


        Scanner scanner= new Scanner(System.in);

        System.out.println("Who are you?");
        String employee = scanner.nextLine();

        System.out.println("How many holidays do you want to request?");
        Integer nrOfHolidays = Integer.valueOf(scanner.nextLine());

        System.out.println("Why do you need them?");
        String description = scanner.nextLine();


        //接下来，我们使用RuntimeService启动一个流程实例。收集的数据作为一个java.util.Map实例传递，其中的键就是之后用于获取变量的标识符。
        //这个流程实例使用key启动。这个key就是BPMN 2.0 XML文件中设置的id属性，在这个例子里是holidayRequest。

        RuntimeService runtimeService = processEngine.getRuntimeService();

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("employee", employee);
        variables.put("nrOfHolidays", nrOfHolidays);
        variables.put("description", description);
        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey("holidayRequest", variables);

    }
}
