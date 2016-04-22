package org.stagemonitor.requestmonitor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.core.instrument.StagemonitorByteBuddyTransformer;

public class AbstractMonitorRequestsTransformer extends StagemonitorByteBuddyTransformer {

	@Override
	protected Class<? extends StagemonitorByteBuddyTransformer> getAdviceClass() {
		return AbstractMonitorRequestsTransformer.class;
	}

	@Advice.OnMethodEnter(inline = false)
	public static void monitorStart(@Advice.BoxedArguments Object[] args, @RequestName String requestName,
									 @Advice.Origin("#m") String methodName, @Advice.This Object thiz) {
		final MonitoredMethodRequest monitoredRequest = new MonitoredMethodRequest(requestName, null, args);
		final RequestMonitorPlugin requestMonitorPlugin = Stagemonitor.getPlugin(RequestMonitorPlugin.class);
		requestMonitorPlugin.getRequestMonitor().monitorStart(monitoredRequest);
		final RequestTrace request = RequestMonitor.getRequest();
		if (requestName == null && request != null) {
			request.setName(requestMonitorPlugin
					.getBusinessTransactionNamingStrategy()
					.getBusinessTransationName(thiz.getClass().getName(), methodName));
		}
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class, inline = false)
	public static void monitorStop(@Advice.Thrown Throwable exception) {
		final RequestMonitor requestMonitor = Stagemonitor.getPlugin(RequestMonitorPlugin.class).getRequestMonitor();
		if (exception != null && exception instanceof Exception) {
			requestMonitor.recordException((Exception) exception);
		}
		requestMonitor.monitorStop();
	}

	@Override
	protected int getOrder() {
		return Integer.MIN_VALUE;
	}

	@Override
	protected List<StagemonitorDynamicValue<?>> getDynamicValues() {
		return Collections.<StagemonitorDynamicValue<?>>singletonList(new RequestNameDynamicValue());
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface RequestName {
	}

	public static class RequestNameDynamicValue extends StagemonitorDynamicValue<RequestName> {

		@Override
		public Class<RequestName> getAnnotationClass() {
			return RequestName.class;
		}

		@Override
		public Object resolve(MethodDescription.InDefinedShape instrumentedMethod,
							  ParameterDescription.InDefinedShape target,
							  AnnotationDescription.Loadable<RequestName> annotation,
							  boolean initialized) {
			final AnnotationDescription.Loadable<MonitorRequests> monitorRequestsLoadable = instrumentedMethod.getDeclaredAnnotations().ofType(MonitorRequests.class);
			if (monitorRequestsLoadable != null) {
				final MonitorRequests monitorRequests = monitorRequestsLoadable.loadSilent();
				if (!monitorRequests.requestName().isEmpty()) {
					return monitorRequests.requestName();
				}
				if (monitorRequests.resolveNameAtRuntime()) {
					return null;
				}
			}
			final String typeName = instrumentedMethod.getDeclaringType().getName();
			return configuration.getConfig(RequestMonitorPlugin.class).getBusinessTransactionNamingStrategy()
					.getBusinessTransationName(typeName, instrumentedMethod.getName());
		}
	}

}
