package com.flowable.sparrow;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HolidayRequest {
    public static void main(String[] args){

        ////创建流程引擎
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        ProcessEngine processEngine = cfg.buildProcessEngine();


        ////部署流程定义
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


        ////启动流程实例
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




        //在Flowable中，数据库事务扮演了关键角色，用于保证数据一致性，并解决并发问题。当调用Flowable API时，默认情况下，所有操作都是同步的，并处于同一个事务下。这意味着，当方法调用返回时，会启动并提交一个事务。

        //流程启动后，会有一个数据库事务从流程实例启动时持续到下一个等待状态。在这个例子里，指的是第一个用户任务。当引擎到达这个用户任务时，状态会持久化至数据库，提交事务，并返回API调用处。

        //在Flowable中，当一个流程实例运行时，总会有一个数据库事务从前一个等待状态持续到下一个等待状态。数据持久化之后，可能在数据库中保存很长时间，甚至几年，直到某个API调用使流程实例继续执行。请注意当流程处在等待状态时，不会消耗任何计算或内存资源，直到下一次APi调用。

        //要获得实际的任务列表，需要通过TaskService创建一个TaskQuery。

        TaskService taskService = processEngine.getTaskService();
        List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();
        System.out.println("You have " + tasks.size() + " tasks:");
        for (int i=0; i<tasks.size(); i++) {
            System.out.println((i+1) + ") " + tasks.get(i).getName());
        }

        //可以使用任务Id获取特定流程实例的变量，并在屏幕上显示实际的申请：
        System.out.println("Which task would you like to complete?");
        int taskIndex = Integer.valueOf(scanner.nextLine());
        Task task = tasks.get(taskIndex - 1);
        Map<String, Object> processVariables = taskService.getVariables(task.getId());
        System.out.println(processVariables.get("employee") + " wants " +
                processVariables.get("nrOfHolidays") + " of holidays. Do you approve this?");

        //在这里，我们在完成任务时传递带有’approved’变量（这个名字很重要，因为之后会在顺序流的条件中使用！）的map来模拟：
        boolean approved = scanner.nextLine().toLowerCase().equals("y");
        variables = new HashMap<String, Object>();
        variables.put("approved", approved);
        taskService.complete(task.getId(), variables);


        //选择使用Flowable这样的流程引擎的原因之一，是它可以自动存储所有流程实例的审计数据或历史数据。这些数据可以用于创建报告，深入展现组织运行的情况，瓶颈在哪里，等等。
        HistoryService historyService = processEngine.getHistoryService();
        List<HistoricActivityInstance> activities =
                historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstance.getId())
                        .finished()
                        .orderByHistoricActivityInstanceEndTime().asc()
                        .list();

        for (HistoricActivityInstance activity : activities) {
            System.out.println(activity.getActivityId() + " took "
                    + activity.getDurationInMillis() + " milliseconds");
        }

    }
}
