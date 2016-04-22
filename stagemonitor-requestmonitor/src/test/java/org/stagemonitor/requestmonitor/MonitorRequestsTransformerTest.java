package org.stagemonitor.requestmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.stagemonitor.core.metrics.metrics2.MetricName.name;

import java.util.Collections;
import java.util.Map;

import com.codahale.metrics.Timer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stagemonitor.core.MeasurementSession;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.core.metrics.metrics2.Metric2Filter;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.core.metrics.metrics2.MetricName;

public class MonitorRequestsTransformerTest {

	private TestClass testClass;
	private TestClassLevelAnnotationClass testClassLevelAnnotationClass;
	private Metric2Registry metricRegistry;
	private RequestTraceCapturingReporter requestTraceCapturingReporter;

	@BeforeClass
	public static void attachProfiler() {
		Stagemonitor.init();
	}

	@Before
	public void before() throws Exception {
		testClass = new TestSubClass();
		metricRegistry = Stagemonitor.getMetric2Registry();
		testClassLevelAnnotationClass = new TestClassLevelAnnotationClass();
		metricRegistry.removeMatching(Metric2Filter.ALL);
		Stagemonitor.setMeasurementSession(new MeasurementSession("MonitorRequestsTransformerTest", "test", "test"));
		Stagemonitor.startMonitoring().get();
		requestTraceCapturingReporter = new RequestTraceCapturingReporter();
	}

	@AfterClass
	public static void resetStagemonitor() {
		Stagemonitor.reset();
	}

	@Test
	public void testMonitorRequests() throws Exception {
		testClass.monitorMe(1);
		final RequestTrace requestTrace = requestTraceCapturingReporter.get();
		assertEquals(Collections.singletonMap("0", "1"), requestTrace.getParameters());
		assertEquals("MonitorRequestsTransformerTest$TestClass#monitorMe", requestTrace.getName());
		assertEquals(1, requestTrace.getCallStack().getChildren().size());
		final String signature = requestTrace.getCallStack().getChildren().get(0).getSignature();
		assertTrue(signature, signature.contains("org.stagemonitor.requestmonitor.MonitorRequestsTransformerTest$TestClass.monitorMe"));

		final Map<MetricName,Timer> timers = metricRegistry.getTimers();
		assertNotNull(timers.keySet().toString(), timers.get(name("response_time_server").tag("request_name", "MonitorRequestsTransformerTest$TestClass#monitorMe").layer("All").build()));
	}

	@Test
	public void testMonitorRequestsThrowingException() throws Exception {
		try {
			testClass.monitorThrowException();
			fail();
		} catch (NullPointerException e) {
			// expected
		}
		final RequestTrace requestTrace = requestTraceCapturingReporter.get();
		assertEquals(NullPointerException.class.getName(), requestTrace.getExceptionClass());

		final Map<MetricName,Timer> timers = metricRegistry.getTimers();
		assertNotNull(timers.keySet().toString(), timers.get(name("response_time_server").tag("request_name", "MonitorRequestsTransformerTest$TestClass#monitorThrowException").layer("All").build()));
	}

	@Test
	public void testMonitorRequestsAnnonymousInnerClass() throws Exception {
		testClass.monitorAnnonymousInnerClass();
		final RequestTrace requestTrace = requestTraceCapturingReporter.get();
		assertEquals("MonitorRequestsTransformerTest$TestClass$1#run", requestTrace.getName());
		assertEquals(1, requestTrace.getCallStack().getChildren().size());
		final String signature = requestTrace.getCallStack().getChildren().get(0).getSignature();
		assertTrue(signature, signature.contains("org.stagemonitor.requestmonitor.MonitorRequestsTransformerTest$TestClass$1.run"));

		final Map<MetricName,Timer> timers = metricRegistry.getTimers();
		assertNotNull(timers.keySet().toString(), timers.get(name("response_time_server").tag("request_name", "MonitorRequestsTransformerTest$TestClass$1#run").layer("All").build()));
	}

	@Test
	public void testMonitorRequestsResolvedAtRuntime() throws Exception {
		testClass.resolveNameAtRuntime();
		final RequestTrace requestTrace = requestTraceCapturingReporter.get();
		assertEquals("MonitorRequestsTransformerTest$TestSubClass#resolveNameAtRuntime", requestTrace.getName());
	}

	@Test
	public void testMonitorRequestsCustomName() throws Exception {
		testClass.doFancyStuff();
		final RequestTrace requestTrace = requestTraceCapturingReporter.get();
		assertEquals("My Cool Method", requestTrace.getName());
	}

	private static class TestClass {
		@MonitorRequests
		public int monitorMe(int i) throws Exception {
			return i;
		}

		@MonitorRequests(resolveNameAtRuntime = true)
		public void resolveNameAtRuntime() throws Exception {
		}

		@MonitorRequests(requestName = "My Cool Method")
		public void doFancyStuff() throws Exception {
		}

		public void monitorAnnonymousInnerClass() {
			new Runnable() {
				@Override
				@MonitorRequests
				public void run() {

				}
			}.run();
		}

		@MonitorRequests
		public int monitorThrowException() throws Exception {
			throw null;
		}
	}

	private static class TestSubClass extends TestClass {
	}

	@Test
	public void testClassLevelAnnotationClass() throws Exception {
		testClassLevelAnnotationClass.monitorMe("1");
		testClassLevelAnnotationClass.dontMonitorMe();
		final RequestTrace requestTrace = requestTraceCapturingReporter.get();
		assertEquals(Collections.singletonMap("0", "1"), requestTrace.getParameters());
		assertEquals("MonitorRequestsTransformerTest$TestClassLevelAnnotationClass#monitorMe", requestTrace.getName());
		assertEquals(1, requestTrace.getCallStack().getChildren().size());
		final String signature = requestTrace.getCallStack().getChildren().get(0).getSignature();
		assertTrue(signature, signature.contains("org.stagemonitor.requestmonitor.MonitorRequestsTransformerTest$TestClassLevelAnnotationClass.monitorMe"));

		final Map<MetricName, Timer> timers = metricRegistry.getTimers();
		assertNotNull(timers.keySet().toString(), timers.get(name("response_time_server").tag("request_name", "MonitorRequestsTransformerTest$TestClassLevelAnnotationClass#monitorMe").layer("All").build()));
	}


	@MonitorRequests
	private static class TestClassLevelAnnotationClass {

		public String monitorMe(String s) throws Exception {
			return s;
		}

		private int dontMonitorMe() throws Exception {
			return 0;
		}
	}

}
