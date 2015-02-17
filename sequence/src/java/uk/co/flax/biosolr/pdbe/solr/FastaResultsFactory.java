package uk.co.flax.biosolr.pdbe.solr;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.rpc.ServiceException;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import uk.ac.ebi.webservices.axis1.stubs.fasta.InputParameters;
import uk.ac.ebi.webservices.axis1.stubs.fasta.JDispatcherService_PortType;
import uk.ac.ebi.webservices.axis1.stubs.fasta.JDispatcherService_Service;
import uk.ac.ebi.webservices.axis1.stubs.fasta.JDispatcherService_ServiceLocator;
import uk.ac.ebi.webservices.axis1.stubs.fasta.WsResultType;
import uk.co.flax.biosolr.pdbe.FastaJob;
import uk.co.flax.biosolr.pdbe.FastaStatus;

/**
 * Connect to FASTA service and generate a PDB id filter based on a user supplied
 * sequence.
 * 
 * program = ssearch
 * database = pdb
 * stype = protein
 * 
 * explowlim = 0.0d
 * scores = 1000
 * alignments = 1000
 */
public class FastaResultsFactory implements XJoinResultsFactory {
	
	// initialisation parameters
	public static final String INIT_EMAIL = "email";
	public static final String INIT_PROGRAM = "program";
	public static final String INIT_DATABASE = "database";
	public static final String INIT_STYPE = "stype";
	public static final String INIT_DEBUG_FILE = "debug.file";
	
	// request parameters
	public static final String FASTA_EXPLOWLIM = "explowlim";
	public static final String FASTA_EXPUPPERLIM = "expupperlim";
	public static final String FASTA_SEQUENCE = "sequence";
	public static final String FASTA_SCORES = "scores";
	public static final String FASTA_ALIGNMENTS = "alignments";
	
	private JDispatcherService_PortType fasta;
	private String email;
    private InputParameters params;
    private String debugFile;

	@Override
	@SuppressWarnings("rawtypes")
	public void init(NamedList args) {
		debugFile = (String)args.get(INIT_DEBUG_FILE);
		if (debugFile != null) {
			try {
				byte[] result = Files.readAllBytes(Paths.get(debugFile));
				fasta = mock(JDispatcherService_PortType.class);
				when(fasta.getStatus(null)).thenReturn(FastaStatus.DONE);
				WsResultType[] types = new WsResultType[] { mock(WsResultType.class) };
				when(fasta.getResultTypes(null)).thenReturn(types);
				when(fasta.getResult(null, null, null)).thenReturn(result);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
	        JDispatcherService_Service service = new JDispatcherService_ServiceLocator();
	        try {
				fasta = service.getJDispatcherServiceHttpPort();
			} catch (ServiceException e) {
				throw new RuntimeException(e);
			}
		}
		
        email = (String)args.get(INIT_EMAIL);
        params = new InputParameters();
        params.setProgram((String)args.get(INIT_PROGRAM));
        params.setDatabase(new String[] { (String)args.get(INIT_DATABASE) });
        params.setStype((String)args.get(INIT_STYPE));
	}
	
	private String getParam(SolrParams params, String name) {
	    String value = params.get(name);
	    if (value == null || value.length() == 0) {
	    	throw new RuntimeException("Missing or empty " + name);
	    }
		return value;
	}
	
	/**
	 * Call out to the FASTA service and add a filter query based on the response.
	 */
	@Override
	public XJoinResults getResults(SolrParams params) throws IOException {
	    if (debugFile == null) {
	        this.params.setSequence(getParam(params, FASTA_SEQUENCE));
	        this.params.setExplowlim(new Double(getParam(params, FASTA_EXPLOWLIM)));
	        this.params.setExpupperlim(new Double(getParam(params, FASTA_EXPUPPERLIM)));
	        this.params.setScores(new Integer(getParam(params, FASTA_SCORES)));
	        this.params.setAlignments(new Integer(getParam(params, FASTA_ALIGNMENTS)));
	    }
	    
        FastaJob job = new FastaJob(fasta, email, this.params);
		job.run();
		
		if (! job.resultsOk()) {
			if (job.getException() != null) {
				throw new RuntimeException(job.getException());
			}
			if (! FastaStatus.DONE.equals(job.getStatus())) {
				throw new RuntimeException("Unexpected FASTA job status: " + job.getStatus());
			}
			if (job.isInterrupted()) {
				throw new RuntimeException("FASTA job was interrupted");
			}
			throw new RuntimeException("No results");
		}
		
		return job.getResults();
	}

}