package cho.carbon.factory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import cho.carbon.fuse.fg.CheckFuncGroup;
import cho.carbon.fuse.fg.FetchFuncGroup;
import cho.carbon.fuse.fg.FirstRoundImproveFuncGroup;
import cho.carbon.fuse.fg.FuseCallBackFuncGroup;
import cho.carbon.fuse.fg.IdentityQueryFuncGroup;
import cho.carbon.fuse.fg.QueryJunctionFuncGroup;
import cho.carbon.fuse.fg.SecondRoundImproveFuncGroup;
import cho.carbon.fuse.fg.ThirdRoundImproveFuncGroup;
import cho.carbon.utils.JavaCompilerFactory;
import cho.carbon.utils.MessageDTO;

public class FGInterfaceListener  implements ApplicationContextAware, BeanFactoryAware {

	private ApplicationContext applicationContext;
	
	private BeanFactory beanFactory;
	
	@Autowired
	RabbitTemplate rabbitTemplate;
	
	@RabbitListener(queues = "carbonqueu")
	public void receive(MessageDTO message) throws Exception {
		
		// 获取beanFactory
		DefaultListableBeanFactory beanDefaultFactory = ((DefaultListableBeanFactory)beanFactory);
		
		System.out.println(message.toString());
		
		// 测试beanFactory 是否可用， 之后需要删除
		testBeanFactory(beanDefaultFactory);
		
		// 这里创建fgbean， 并交给spring管理
		
		String applicationName = message.getApplicationName();
		
		Map<String, List<Class<?>>> mapInter = message.getMapInter();
		Iterator<Entry<String, List<Class<?>>>> iterator = mapInter.entrySet().iterator();
		
		while (iterator.hasNext()) {
			Entry<String, List<Class<?>>> next = iterator.next();
			
			String itemModelCode = next.getKey();
			
			// 创建feign客户端
			Class feignClient = buildFeignClient(applicationName, itemModelCode, next.getValue());
			// 创建FG 实例
			Class fgBean = buildFGBean(feignClient,applicationName, itemModelCode, next.getValue());
			
			// 创建配置类, 用来扫描feign客户端
			// 设置配置类的名称， 每个item code  都有一个配置类
			Class buildConfigClazz = buildConfigClazz(itemModelCode);
			
			String configName = buildConfigClazz.getName();
			boolean containsFeign = beanDefaultFactory.containsBean(configName);
			System.out.println("===========" + containsFeign);
			if (!containsFeign) {
				BeanDefinition configBeanDefinition = new RootBeanDefinition(buildConfigClazz);
				beanDefaultFactory.registerBeanDefinition(configName, configBeanDefinition);
				System.out.println("注入了bean  ... configBeanDefinition");
			}
			
			String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
			System.out.println("----------------bean start -------------------");
			
			for (String name : beanDefinitionNames) {
				System.out.println(name);
			}
			
			System.out.println("----------------bean end-------------------");
			
			Object bean = beanDefaultFactory.getBean(feignClient);
			
			// 创建fg
			String fgBeanSimpleName = fgBean.getSimpleName();
			String fgBeanLowerCase = fgBeanSimpleName.toLowerCase();
			boolean containsBean = beanDefaultFactory.containsBean(fgBeanLowerCase);
			System.out.println("===========" + containsBean);
			if (!containsBean) {
				BeanDefinition fgBeanDefinition = new RootBeanDefinition(fgBean);
				beanDefaultFactory.registerBeanDefinition(fgBeanLowerCase, fgBeanDefinition);
				System.out.println("注入了bean  ... fgBeanDefinition");
			}
//			
			Object bean2 = beanDefaultFactory.getBean(fgBeanLowerCase);
//			
			System.out.println(bean2);
			
		}
		
		
		
	}

	// 配置文件类
	private Class buildConfigClazz(String itemModelCode) throws Exception {
		String packageName = "cho.carbon.config";
		String clazzName =  itemModelCode + "Config";
		
		StringBuffer sb = new StringBuffer();
		sb.append("package "+packageName+";")
		.append("import org.springframework.cloud.openfeign.EnableFeignClients;")
		.append("import org.springframework.context.annotation.Configuration;")
		.append("@Configuration")
		.append("@EnableFeignClients(basePackages= {\"cho.carbon\"})")
		.append("public class "+clazzName+" {")
		.append("}");
		    
		return JavaCompilerFactory.compilerJavaFile(packageName, clazzName, sb.toString().getBytes(), false, false);
	}

	/**
	 * @param applicationName 引用名称
	 * @param feignClient  需要调用的feign 实例
	 * @param fgCalzzName  fg实例名称 
	 * @param string 
	 * @param interfaceList fg实例实现的接口
	 * @throws Exception 
	 */
	private Class buildFGBean(Class feignClient, String applicationName, String itemModelCode, List<Class<?>> interfaceList) throws Exception {
	
		String feignClientName = feignClient.getSimpleName().toLowerCase();
		// 定义换行符
		String rt = "\r\n";
		String fgCalzzNameLowerCase = itemModelCode.toLowerCase();
		String packageName = "cho.carbon.fg";
		String clazzName =  itemModelCode +  "RemoteService";
		
		StringBuffer sb = new StringBuffer();
		sb.append("package "+packageName+";" + rt);
		sb.append("import java.util.Collection;"+ rt);
		sb.append("import org.springframework.beans.factory.annotation.Autowired;"+ rt);
		sb.append("import cho.carbon.complexus.FGRecordComplexus;"+ rt);
		sb.append("import cho.carbon.context.fg.FuncGroupContext;"+ rt);
		sb.append("import cho.carbon.dto.CarbonParam;"+ rt);
		sb.append("import cho.carbon.fuse.fg.CheckFGResult;"+ rt);
		sb.append("import cho.carbon.fuse.fg.ConJunctionFGResult;"+ rt);
		sb.append("import cho.carbon.fuse.fg.FGOSerializableFactory;"+ rt);
		sb.append("import cho.carbon.fuse.fg.FetchFGResult;"+ rt);
		sb.append("import cho.carbon.fuse.fg.FunctionGroup;"+ rt);
		sb.append("import cho.carbon.fuse.fg.ImproveFGResult;"+ rt);
		sb.append("import cho.carbon.meta.criteria.model.ModelConJunction;"+ rt);
		sb.append("import cho.carbon.meta.criteria.model.ModelCriterion;"+ rt);
		sb.append("import cho.carbon.ops.complexus.OpsComplexus;"+ rt);
		sb.append("import cho.carbon.rrc.record.FGRootRecord;"+ rt);
		
		sb.append("public class "+clazzName+" implements FunctionGroup");
		
		StringBuffer javaCenter = new StringBuffer();
		
		for (Class<?> interClazz : interfaceList) {
			boolean flag = false;
			
			// 根据接口生成fg 实例 方法
			if (IdentityQueryFuncGroup.class.equals(interClazz)) {
				flag = true;
				//runningAfterCodeQuery 方法
				javaCenter.append(" @Override" +rt)
				.append("public boolean runningAfterCodeQuery() {" +rt)
				.append("return "+feignClientName+".runningAfterCodeQuery();" + rt)
				.append("}" +rt);
				
				//getCriterions
				javaCenter.append("@Override"  +rt )
				.append("public Collection<ModelCriterion> getCriterions(String recordCode, FGRecordComplexus fGRecordComplexus) {" + rt)
				.append("CarbonParam carbonParam = new CarbonParam();" + rt)
				.append("carbonParam.setRecordCode(recordCode);" + rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());" + rt)
				.append("return FGOSerializableFactory.des2Criterions("+feignClientName+".getCriterions(carbonParam));" + rt)
				.append("}" + rt);
			} else if (CheckFuncGroup.class.equals(interClazz)) {
				flag = true;
				// afterCheck
				javaCenter.append("@Override"  +rt)
				.append("public CheckFGResult afterCheck(FuncGroupContext funcGroupContext, String recordCode, FGRecordComplexus fGRecordComplexus) {" + rt)
				.append("CarbonParam carbonParam = new CarbonParam();" + rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());" + rt)
				.append("carbonParam.setRecordCode(recordCode);" + rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());" + rt)
				.append("return FGOSerializableFactory.des2CheckFGResult("+feignClientName+".afterCheck(carbonParam));" + rt)
				.append("}" + rt);
				
			} else if (ThirdRoundImproveFuncGroup.class.equals(interClazz)) {
				flag = true;
				// thirdImprove
				javaCenter.append("@Override"+rt)
				.append("public ImproveFGResult thirdImprove(FuncGroupContext funcGroupContext, String recordCode, FGRecordComplexus fGRecordComplexus) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setRecordCode(recordCode);"+rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());"+rt)
				.append("return FGOSerializableFactory.des2ImproveFGResult("+feignClientName+".thirdImprove(carbonParam));"+rt)
				.append("}"+rt);
			} else if (FuseCallBackFuncGroup.class.equals(interClazz)) {
				flag = true;
				//afterFusition
				javaCenter.append("@Override"+rt)
				.append("public boolean afterFusition(FuncGroupContext funcGroupContext, String recordCode) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setRecordCode(recordCode);"+rt)
				.append("return  "+feignClientName+".afterFusition(carbonParam);"+rt)
				.append("}"+rt);
			} else if (FetchFuncGroup.class.equals(interClazz)) {
				flag = true;
				// fetchImprove
				javaCenter.append(" @Override"+rt)
				.append("public FetchFGResult fetchImprove(FuncGroupContext funcGroupContext, FGRootRecord record) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setfGRootRecord(record.serialize());"+rt)
				.append(" return FGOSerializableFactory.des2FetchFGResult( "+feignClientName+".fetchImprove(carbonParam));"+rt)
				.append("}"+rt);
			}else if (QueryJunctionFuncGroup.class.equals(interClazz)) {
				flag = true;
				//junctionImprove
				javaCenter.append(" @Override"+rt)
				.append("public ConJunctionFGResult junctionImprove(FuncGroupContext funcGroupContext, ModelConJunction modelConJunction) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setModelConJunction(modelConJunction.serialize());"+rt)
				.append("return FGOSerializableFactory.des2ConJunctionFGResult("+feignClientName+".junctionImprove(carbonParam));"+rt)
				.append("}"+rt);
			}else if (SecondRoundImproveFuncGroup.class.equals(interClazz)) {
				flag = true;
				//secondImprove
				javaCenter.append(" @Override"+rt)
				.append("public ImproveFGResult secondImprove(FuncGroupContext funcGroupContext, String record, FGRecordComplexus fGRecordComplexus) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setRecordCode(record);"+rt)
				.append("return FGOSerializableFactory.des2ImproveFGResult("+feignClientName+".secondImprove(carbonParam));"+rt)
				.append("}"+rt);
			}else if (FirstRoundImproveFuncGroup.class.equals(interClazz)) {
				flag = true;
				//preImprove
				javaCenter.append("@Override"+rt)
				.append("public ImproveFGResult preImprove(FuncGroupContext funcGroupContext, String recordCode, OpsComplexus opsComplexus, FGRecordComplexus fGRecordComplexus) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setRecordCode(recordCode);"+rt)
				.append("carbonParam.setOpsComplexus(opsComplexus.serialize());"+rt)
				.append("return FGOSerializableFactory.des2ImproveFGResult("+feignClientName+".preImprove(carbonParam));"+rt)
				.append("}"+rt);
				
				// improve
				javaCenter.append("@Override"+rt)
				.append("public ImproveFGResult improve(FuncGroupContext funcGroupContext, String recordCode, FGRecordComplexus fGRecordComplexus) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setRecordCode(recordCode);"+rt)
				.append("return FGOSerializableFactory.des2ImproveFGResult("+feignClientName+".improve(carbonParam));"+rt)
				.append("}" + rt);
				// postImprove
				javaCenter.append(" @Override"+rt)
				.append("public ImproveFGResult postImprove(FuncGroupContext funcGroupContext, String recordCode, FGRecordComplexus fGRecordComplexus) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setfGRecordComplexus(fGRecordComplexus.serialize());"+rt)
				.append("carbonParam.setFuncGroupContext(funcGroupContext.serialize());"+rt)
				.append("carbonParam.setRecordCode(recordCode);"+rt)
				.append("return FGOSerializableFactory.des2ImproveFGResult("+feignClientName+".postImprove(carbonParam));"+rt)
				.append("}"+rt);
				
				// improveOnlyCorrelativeRelation
				javaCenter.append("@Override"+rt)
				.append("public boolean improveOnlyCorrelativeRelation() {"+rt)
				.append("return "+feignClientName+".improveOnlyCorrelativeRelation();"+rt)
				.append("}"+rt);
				
				// improveEveryTime
				javaCenter.append(" @Override"+rt)
				.append("public boolean improveEveryTime() {"+rt)
				.append("return "+feignClientName+".improveEveryTime();"+rt)
				.append("}"+rt);
				// needImprove
				javaCenter.append(" @Override"+rt)
				.append("public boolean needImprove(String recordCode, OpsComplexus opsComplexus) {"+rt)
				.append("CarbonParam carbonParam = new CarbonParam();"+rt)
				.append("carbonParam.setRecordCode(recordCode);"+rt)
				.append("carbonParam.setOpsComplexus(opsComplexus.serialize());"+rt)
				.append("return "+feignClientName+".needImprove(carbonParam);"+rt)
				.append("}"+rt);
			}
			
			if(flag) {
				sb.append(", " + interClazz.getName());
			}
			
		}
		
		sb.append(" {"  + rt);
		
		sb.append("@Autowired" + rt);
		sb.append("private " + feignClient.getName() + " " + feignClientName + ";"  + rt);
		sb.append(javaCenter.toString());
		sb.append("}" + rt);
		
		
		System.out.println(sb.toString());
		return JavaCompilerFactory.compilerJavaFile(packageName, clazzName, sb.toString().getBytes(), false, false);
	}

	/**
	 * 创建feign 客户端接口
	 * @param applicationName 引用名称
	 * @param fgCalzzName  fg实例名称 
	 * @param interfaceList fg实例实现的接口
	 * @throws Exception 
	 */
	private  Class buildFeignClient(String applicationName, String fgCalzzName, List<Class<?>> interfaceList) throws Exception {
		String fgCalzzNameUpperCase = fgCalzzName.toUpperCase();
		String fgCalzzNameLowerCase = fgCalzzName.toLowerCase();

		applicationName = applicationName.toUpperCase();
		String clazzName =  fgCalzzNameUpperCase+ "FeignClientService";
		
		System.out.println("feign的名称 ： " + clazzName);
		// 定义换行符
		String rt = "\r\n";
		StringBuffer sb  = new StringBuffer();
		String packageName = "cho.carbon.service";
		sb.append("package  "+packageName+";" +rt);
		sb.append("import org.springframework.cloud.openfeign.FeignClient;"+ rt)
		.append("import org.springframework.web.bind.annotation.RequestMapping;" + rt)
		.append("import cho.carbon.dto.CarbonParam;" + rt)
		.append("import org.springframework.web.bind.annotation.RequestBody;"  + rt)
		.append("@FeignClient(value = \""+applicationName+"\")" + rt)
		.append("public interface "+clazzName+" {" + rt);
		
		// 遍历所有接口
		for (Class<?> interClazz : interfaceList) {
			// 根据接口生成feign 方法
			if (IdentityQueryFuncGroup.class.equals(interClazz)) {
				//runningAfterCodeQuery 方法
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/runningAfterCodeQuery\")" +rt)
				.append(" public boolean runningAfterCodeQuery();" +rt);
				
				//getCriterions
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/getCriterions\")" +rt)
				.append(" public String getCriterions(@RequestBody CarbonParam carbonParam);" +rt);
			} else if (CheckFuncGroup.class.equals(interClazz)) {
				// afterCheck
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/afterCheck\")" +rt)
				.append(" public String afterCheck(@RequestBody CarbonParam carbonParam);" +rt);
				
			} else if (ThirdRoundImproveFuncGroup.class.equals(interClazz)) {
				// thirdImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/thirdImprove\")"+rt)
				.append(" public String thirdImprove(@RequestBody CarbonParam carbonParam);"+rt);
			} else if (FuseCallBackFuncGroup.class.equals(interClazz)) {
				//afterFusition
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/afterFusition\")"+rt)
				.append(" public boolean afterFusition(@RequestBody CarbonParam carbonParam);"+rt);
			} else if (FetchFuncGroup.class.equals(interClazz)) {
				// fetchImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/fetchImprove\")"+rt)
				.append(" public String fetchImprove(@RequestBody CarbonParam carbonParam);"+rt);
			}else if (QueryJunctionFuncGroup.class.equals(interClazz)) {
				//junctionImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/junctionImprove\")"+rt)
				.append(" public String junctionImprove(@RequestBody CarbonParam carbonParam);"+rt);
				
			}else if (SecondRoundImproveFuncGroup.class.equals(interClazz)) {
				//secondImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/secondImprove\")"+rt)
				.append(" public String secondImprove(@RequestBody CarbonParam carbonParam);"+rt);
			}else if (FirstRoundImproveFuncGroup.class.equals(interClazz)) {
				//preImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/preImprove\")"+rt)
				.append(" public String preImprove(@RequestBody CarbonParam carbonParam);"+rt);
				
				// improve
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/improve\")"+rt)
				.append(" public String improve(@RequestBody CarbonParam carbonParam);"+rt);
				
				// postImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/postImprove\")"+rt)
				.append(" public String postImprove(@RequestBody CarbonParam carbonParam);"+rt);
				
				// improveOnlyCorrelativeRelation
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/improveOnlyCorrelativeRelation\")"+rt)
				.append(" public boolean improveOnlyCorrelativeRelation();"+rt);
				
				// improveEveryTime
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/improveEveryTime\")"+rt)
				.append(" public boolean improveEveryTime();"+rt);
				// needImprove
				sb.append(" @RequestMapping(value = \"/"+fgCalzzNameLowerCase+"/needImprove\")"+rt)
				.append(" public boolean needImprove(@RequestBody CarbonParam carbonParam);"+rt);
			}
		}
		
		sb.append(" }" + rt);
		System.out.println("feign接口： " + sb.toString());
		
		return JavaCompilerFactory.compilerJavaFile(packageName, clazzName, sb.toString().getBytes(), false, false);
	}

	// 测试beanFactory 是否可用
	private void testBeanFactory(DefaultListableBeanFactory beanDefaultFactory) {
		// 创建书籍
		boolean containsBeanbook = beanDefaultFactory.containsBean("book");
		System.out.println("===========" + containsBeanbook);
		if (!containsBeanbook) {
			BeanDefinition people = new RootBeanDefinition(Book.class);
			beanDefaultFactory.registerBeanDefinition("book", people);
			System.out.println("注入了bean  ... book");
		}
		
		// 创建人口
		boolean containsBean = beanDefaultFactory.containsBean("people");
		System.out.println("===========" + containsBean);
		if (!containsBean) {
			BeanDefinition people = new RootBeanDefinition(People.class);
			beanDefaultFactory.registerBeanDefinition("people", people);
			System.out.println("注入了bean  ... people");
		}
		
		People bean = beanDefaultFactory.getBean(People.class);
		if (bean!=null) {
			bean.showBook();
		}
	} 
	
	
	
	class People {
		
		@Autowired
		private Book book;
		
		public People() {
			System.out.println("people 创建了");
		}
		private String name = "小明";

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
		
		public void showBook() {
			System.out.println(this.book.getName());
		}
		
	}
	
	class Book {
		public Book() {
			System.out.println("Book 创建了");
		}
		private String name = "历史书";
		
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
	

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		
	}
	
}
