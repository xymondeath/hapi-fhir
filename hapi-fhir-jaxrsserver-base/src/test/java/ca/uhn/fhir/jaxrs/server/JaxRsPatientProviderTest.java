package ca.uhn.fhir.jaxrs.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jaxrs.server.example.JaxRsPatientRestProvider;
import ca.uhn.fhir.jaxrs.server.example.RandomServerPortProvider;
import ca.uhn.fhir.model.api.BundleEntry;
import ca.uhn.fhir.model.dstu2.composite.HumanNameDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Conformance;
import ca.uhn.fhir.model.dstu2.resource.Parameters;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.primitive.BoundCodeDt;
import ca.uhn.fhir.model.primitive.DateDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.model.valueset.BundleEntryTransactionMethodEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.client.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.method.SearchStyleEnum;
import ca.uhn.fhir.rest.server.EncodingEnum;

public class JaxRsPatientProviderTest {

	private static IGenericClient client;
	private static final FhirContext ourCtx = FhirContext.forDstu2();
	private static final String PATIENT_NAME = "Van Houte";	
	private static int ourPort;
	private static Server jettyServer;

	@BeforeClass
	public static void setUpClass()
			throws Exception {
		ourPort = RandomServerPortProvider.findFreePort();
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		System.out.println(ourPort);
		jettyServer = new Server(ourPort);
		jettyServer.setHandler(context);
		ServletHolder jerseyServlet = context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class, "/*");
		jerseyServlet.setInitOrder(0);
		jerseyServlet.setInitParameter("jersey.config.server.provider.classnames", JaxRsPatientRestProvider.class.getCanonicalName());
		jettyServer.start();

		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		client = ourCtx.newRestfulGenericClient("http://localhost:" + ourPort + "/");
		client.setEncoding(EncodingEnum.JSON);
		client.registerInterceptor(new LoggingInterceptor(true));
	}

	@AfterClass
	public static void tearDownClass()
			throws Exception {
		try {
			jettyServer.destroy();
		}
		catch (Exception e) {
		}
	}

    /** Search/Query - Type */
    @Test
    public void findUsingGenericClientBySearch() {
        // Perform a search
        final ca.uhn.fhir.model.api.Bundle results = client.search().forResource(Patient.class)
                .where(Patient.NAME.matchesExactly().value(PATIENT_NAME)).execute();
        System.out.println(results.getEntries().get(0));
        assertEquals(results.getEntries().size(), 1);
    }

    /** Search - Multi-valued Parameters (ANY/OR) */
    @Test
    public void findUsingGenericClientBySearchWithMultiValues() {
        final ca.uhn.fhir.model.api.Bundle response = client.search().forResource(Patient.class)
                .where(Patient.ADDRESS.matches().values("Toronto")).and(Patient.ADDRESS.matches().values("Ontario"))
                .and(Patient.ADDRESS.matches().values("Canada"))
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier("SHORTNAME", "TOYS")).execute();
        System.out.println(response.getEntries().get(0));
    }

    /** Search - Paging */
    @Test
    public void findWithPaging() {
        // Perform a search
        final Bundle results = client.search().forResource(Patient.class).limitTo(8).returnBundle(Bundle.class).execute();
        System.out.println(results.getEntry().size());

        if (results.getLink(Bundle.LINK_NEXT) != null) {

            // load next page
            final Bundle nextPage = client.loadPage().next(results).execute();
            System.out.println(nextPage.getEntry().size());
        }
    }
    
    /** Search using other query options */
    public void testOther() {
        //missing
    }
    
    /** */ 
    @Test
    public void testSearchPost() {
        Bundle response = client.search()
                .forResource("Patient")
                .usingStyle(SearchStyleEnum.POST)
                .returnBundle(Bundle.class)
                .execute();
        assertTrue(response.getEntry().size() > 0);
    }
    
    /** Search - Compartments */
    @Test
    public void testSearchCompartements() {
        Bundle response = client.search()
                .forResource(Patient.class)
                .withIdAndCompartment("1", "Condition")
                .returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
                .execute();
        assertTrue(response.getEntry().size() > 0);
    }
    
    /** Search - Subsetting (_summary and _elements) */
    @Test
    @Ignore
    public void testSummary() {
    Object response = client.search()
            .forResource(Patient.class)
            .returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
            .execute();
    }
    
    @Test
    public void testCreatePatient() {
        final Patient existing = new Patient();
        existing.setId((IdDt) null);
        existing.getNameFirstRep().addFamily("Created Patient 54");
        client.setEncoding(EncodingEnum.JSON);
        final MethodOutcome results = client.create().resource(existing).prefer(PreferReturnEnum.REPRESENTATION).execute();
        System.out.println(results.getId());
        final Patient patient = (Patient) results.getResource();
        System.out.println(patient);
        assertNotNull(client.read(patient.getId()));
        client.setEncoding(EncodingEnum.JSON);
    }
    
    
    /** Conditional Creates */
    @Test
    public void testConditionalCreate() {
        final Patient existing = new Patient();
        existing.setId((IdDt) null);
        existing.getNameFirstRep().addFamily("Created Patient 54");
        client.setEncoding(EncodingEnum.XML);
        final MethodOutcome results = client.create().resource(existing).prefer(PreferReturnEnum.REPRESENTATION).execute();
        System.out.println(results.getId());
        final Patient patient = (Patient) results.getResource();

        client.create()
        .resource(patient)
        .conditional()
        .where(Patient.IDENTIFIER.exactly().identifier(patient.getIdentifierFirstRep()))
        .execute();
    }
    
    
    /** Find By Id */
    @Test
    public void findUsingGenericClientById() {
        final Patient results = client.read(Patient.class, "1");
        assertEquals(results.getId().getIdPartAsLong().longValue(), 1L);
    }

    @Test
    public void testUpdateById() {
        final Patient existing = client.read(Patient.class, "1");
        final List<HumanNameDt> name = existing.getName();
        name.get(0).addSuffix("The Second");
        existing.setName(name);
        client.setEncoding(EncodingEnum.XML);
        final MethodOutcome results = client.update("1", existing);
    }

    @Test
    public void testDeletePatient() {
        final Patient existing = new Patient();
        existing.getNameFirstRep().addFamily("Created Patient XYZ");
        final MethodOutcome results = client.create().resource(existing).prefer(PreferReturnEnum.REPRESENTATION).execute();
        System.out.println(results.getId());
        final Patient patient = (Patient) results.getResource();
        client.delete(Patient.class, patient.getId());
        try {
            client.read(patient.getId());
            fail();
        }
        catch (final Exception e) {
            //assertEquals(e.getStatusCode(), Constants.STATUS_HTTP_404_NOT_FOUND);
        }
    }
    
    /** Transaction - Server */
    @Ignore
    @Test
    public void testTransaction() {
        ca.uhn.fhir.model.api.Bundle bundle = new ca.uhn.fhir.model.api.Bundle();
        BundleEntry entry = bundle.addEntry();
        final Patient existing = new Patient();
        existing.getNameFirstRep().addFamily("Created with bundle");
        entry.setResource(existing);
        
        BoundCodeDt<BundleEntryTransactionMethodEnum> theTransactionOperation = 
                new BoundCodeDt(
                        BundleEntryTransactionMethodEnum.VALUESET_BINDER, 
                        BundleEntryTransactionMethodEnum.POST);
        entry.setTransactionMethod(theTransactionOperation);
        ca.uhn.fhir.model.api.Bundle response = client.transaction().withBundle(bundle).execute();
    }
    
    /** Conformance - Server */
    @Test
    @Ignore
    public void testConformance() {
        final Conformance conf = client.fetchConformance().ofType(Conformance.class).execute();
        System.out.println(conf.getRest().get(0).getResource().get(0).getType());
        assertEquals(conf.getRest().get(0).getResource().get(0).getType().toString(), "Patient");
    }    
    
    /** Extended Operations */
 // Create a client to talk to the HeathIntersections server
    @Test
    public void testExtendedOperations() {
        client.registerInterceptor(new LoggingInterceptor(true));
         
        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("start").setValue(new DateDt("2001-01-01"));
        inParams.addParameter().setName("end").setValue(new DateDt("2015-03-01"));
        inParams.addParameter().setName("dummy").setValue(new StringDt("myAwesomeDummyValue"));
         
        // Invoke $everything on "Patient/1"
        Parameters outParams = client
           .operation()
           .onInstance(new IdDt("Patient", "1"))
           .named("$last")
           .withParameters(inParams)
           //.useHttpGet() // Use HTTP GET instead of POST
           .execute();
        String resultValue = outParams.getParameter().get(0).getValue().toString();
        System.out.println(resultValue);
        assertEquals("expected but found : "+ resultValue, resultValue.contains("myAwesomeDummyValue"), true);
    }
    
    @Test
    public void testExtendedOperationsUsingGet() {
        client.registerInterceptor(new LoggingInterceptor(true));
        
        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("start").setValue(new DateDt("2001-01-01"));
        inParams.addParameter().setName("end").setValue(new DateDt("2015-03-01"));
        inParams.addParameter().setName("dummy").setValue(new StringDt("myAwesomeDummyValue"));
        
        // Invoke $everything on "Patient/1"
        Parameters outParams = client
                .operation()
                .onInstance(new IdDt("Patient", "1"))
                .named("$last")
                .withParameters(inParams)
                .useHttpGet() // Use HTTP GET instead of POST
                .execute();
        String resultValue = outParams.getParameter().get(0).getValue().toString();
        System.out.println(resultValue);
        assertEquals("expected but found : "+ resultValue, resultValue.contains("myAwesomeDummyValue"), true);
    }
    
    @Test
    public void testFindUnknownPatient() {
        try {
            final Patient existing = client.read(Patient.class, "999955541264");
            fail();
        }
        catch (final Exception e) {
            e.printStackTrace();
            //assertEquals(e.getStatusCode(), 404);
        }
    }

    @Test
    public void testVRead() {
        final Patient patient = client.vread(Patient.class, "1", "1");
        System.out.println(patient);
    }
    
    @Test
    public void testRead() {
        final Patient patient = client.read(Patient.class, "1");
        System.out.println(patient);
    }

}
