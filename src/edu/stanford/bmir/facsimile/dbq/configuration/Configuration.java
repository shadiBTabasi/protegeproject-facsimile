package edu.stanford.bmir.facsimile.dbq.configuration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.semanticweb.owlapi.model.IRI;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.bmir.facsimile.dbq.exception.ConfigurationFileParseException;
import edu.stanford.bmir.facsimile.dbq.exception.MissingConfigurationElementException;
import edu.stanford.bmir.facsimile.dbq.form.elements.FormElement.ElementType;
import edu.stanford.bmir.facsimile.dbq.form.elements.QuestionList;
import edu.stanford.bmir.facsimile.dbq.form.elements.QuestionList.QuestionListType;
import edu.stanford.bmir.facsimile.dbq.form.elements.Section.SectionType;
import edu.stanford.bmir.facsimile.dbq.tree.TreeNode;

/**
 * @author Rafael S. Goncalves <br>
 * Stanford Center for Biomedical Informatics Research (BMIR) <br>
 * School of Medicine, Stanford University <br>
 */
public class Configuration {
	private Document doc;
	private String ontPath, outPath, title, cssStyle, formIri;
	private Map<IRI,String> imports;
	private Map<IRI,IRI> subquestionPosTriggers, subquestionNegTriggers;
	private Map<IRI,ElementType> elementTypes;
	private Map<IRI,SectionType> sectionTypes;
	private Map<IRI,List<TreeNode<IRI>>> sections;
	private Map<IRI,List<String>> optionsOrder;
	private Map<IRI,Boolean> sectionNumbering, questionNumbering, questionRequired;
	private List<QuestionList> questionlists;
	private final String delim = ";";
	private File file;
	private boolean verbose;
	
	
	/**
	 * Constructor
	 * @param file	XML document file
	 * @param verbose	true for verbose mode
	 */
	public Configuration(File file, boolean verbose) {
		this.file = file;
		this.verbose = verbose;
		imports = new HashMap<IRI,String>();
		subquestionPosTriggers = new HashMap<IRI,IRI>();
		subquestionNegTriggers = new HashMap<IRI,IRI>();
		elementTypes = new LinkedHashMap<IRI,ElementType>();
		sectionTypes = new LinkedHashMap<IRI,SectionType>();
		optionsOrder = new HashMap<IRI,List<String>>();
		sectionNumbering = new HashMap<IRI,Boolean>();
		questionNumbering = new HashMap<IRI,Boolean>();
		questionRequired = new HashMap<IRI,Boolean>();
		questionlists = new ArrayList<QuestionList>();
	}
	
	
	/**
	 * Constructor 
	 * @param file	XML document file
	 */
	public Configuration(File file) {
		this(file, false);
	}
	
	
	/**
	 * Load configuration file and initialize data structures
	 */
	public void parseConfigurationFile() {
		doc = loadConfigurationFile(file);
		if(doc == null) throw new ConfigurationFileParseException("An error occurred parsing the given XML configuration file. "
				+ "Ensure that the file is well-formed and valid with respect to the given DTD");
		formIri = getFormIRI();
		sections = getSections();
		gatherOntologyFiles();
		gatherOutputInformation();
	}
	
	
	/**
	 * Load XML configuration file 
	 * @param f	File
	 * @return XML document instance 
	 */
	private Document loadConfigurationFile(File f) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document doc = null;
		try {
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		}
		try {
			doc = db.parse(f);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
		return doc;
	}
	
	
	/**
	 * Retrieve output information
	 */
	private void gatherOutputInformation() {
		NodeList nl = doc.getElementsByTagName("output");
		for(int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if(n.hasChildNodes() && n.getChildNodes().getLength() > 0) {
				for(int j = 0; j < n.getChildNodes().getLength(); j++) {
					Node c = n.getChildNodes().item(j);
					if(c.getNodeName().equalsIgnoreCase("file")) {
						outPath = c.getTextContent();
						if(c.hasAttributes() && c.getAttributes().getNamedItem("title") != null)
							title = c.getAttributes().getNamedItem("title").getTextContent();
					}
					if(c.getNodeName().equalsIgnoreCase("cssstyle"))
						cssStyle = c.getTextContent();
				}
			}
		}
	}
	
	
	/**
	 * Retrieve ontology files (including imports)
	 */
	private void gatherOntologyFiles() {
		NodeList nl = doc.getElementsByTagName("ontology");
		for(int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if(n.getParentNode().getNodeName().equalsIgnoreCase("imports")) {
				if(n.hasAttributes()) {
					String iri = n.getAttributes().getNamedItem("iri").getTextContent();
					if(iri != null)
						imports.put(IRI.create(iri), n.getTextContent());
				}
			}
			else if(n.getParentNode().getNodeName().equalsIgnoreCase("input"))
				ontPath = n.getTextContent();
		}
	}
	
	
	/**
	 * Get the IRI of the individual representing the form
	 * @return String representation of the form individual IRI
	 */
	private String getFormIRI() {
		String formIri = "";
		NodeList nl = doc.getElementsByTagName("form");
		loop:
			for(int i = 0; i < nl.getLength(); i++) {
				NodeList nodeList = nl.item(i).getChildNodes();
				for(int j = 0; j < nodeList.getLength(); j++) {
					if(nodeList.item(j).getNodeName().equalsIgnoreCase("iri")) {
						formIri = nodeList.item(j).getTextContent();
						if(verbose) System.out.println(" Form IRI: " + formIri);
						break loop;
					}
				}
			}
		if(formIri.isEmpty()) throw new MissingConfigurationElementException("IRI of form individual is missing");
		return formIri;
	}
	
	
	/**
	 * Gather a map of sections specified in the configuration file and their questions
	 * @return Map of sections' IRIs and their respective questions
	 */
	private Map<IRI,List<TreeNode<IRI>>> getSections() {
		Map<IRI,List<TreeNode<IRI>>> sections = new LinkedHashMap<IRI,List<TreeNode<IRI>>>();
		NodeList nl = doc.getElementsByTagName("section");
		for(int i = 0; i < nl.getLength(); i++) { // foreach section
			Node sectionNode = nl.item(i);
			SectionType type = getSectionType(sectionNode);
			NodeList children = sectionNode.getChildNodes(); // <iri>, (<questionList> | <infoList>)
			List<TreeNode<IRI>> questions = null; IRI section = null;
			for(int j = 0; j < children.getLength(); j++) {
				Node child = children.item(j);
				if(child.getNodeName().equalsIgnoreCase("iri")) { // section iri
					section = IRI.create(child.getTextContent());
					if(verbose) System.out.println("   Section: " + section + " (type: " + type.toString() + ")");
				}
				else if(child.getNodeName().equalsIgnoreCase("questionlist"))
					questions = getQuestions(child, null);
				else if(child.getNodeName().equalsIgnoreCase("infolist"))
					questions = getInfoRequests(child);
			}
			if(section != null && questions != null && !questions.isEmpty()) {
				sections.put(section, questions);
				sectionNumbering.put(section, isSectionNumbered(sectionNode));
				sectionTypes.put(section, type);
			}
		}
		return sections;
	}
	
	
	/**
	 * Get the section type of a given section node
	 * @param n	Section node
	 * @return Section type
	 */
	private SectionType getSectionType(Node n) {
		SectionType type = null;
		if(n.hasAttributes() && n.getAttributes().getNamedItem("type") != null) {
			Node a = n.getAttributes().getNamedItem("type");
			for(SectionType s : SectionType.values())
				if(a.getTextContent().equalsIgnoreCase(s.toString()))
					type = s;
		}
		else
			type = SectionType.QUESTION_SECTION;
		return type;
	}
	
	
	/**
	 * Check whether a given section is declared to be un-numbered
	 * @param n	Section node
	 * @return true if section is numbered, false otherwise
	 */
	private boolean isSectionNumbered(Node n) {
		boolean isNumbered = true;
		if(n.hasAttributes()) {
			Node a = n.getAttributes().getNamedItem("numbered");
			if(a != null)
				isNumbered = Boolean.parseBoolean(a.getTextContent());
		}
		return isNumbered;
	}
	
	
	/**
	 * Gather the questions in a given (questionlist or subquestionlist) node
	 * @param questionListNode	Questionlist node
	 * @param questionTree	Question treenode, if applicable
	 * @return List of question trees
	 */
	private List<TreeNode<IRI>> getQuestions(Node questionListNode, TreeNode<IRI> questionTree) {
		List<TreeNode<IRI>> questions = new ArrayList<TreeNode<IRI>>();
		List<IRI> questionList = new ArrayList<IRI>();
		NodeList nl = questionListNode.getChildNodes(); // <question>'s
		for(int i = 0; i < nl.getLength(); i++) { // foreach <question>
			Node qNode = nl.item(i);
			NodeList children = qNode.getChildNodes(); // (<iri> | <questionList>)
			TreeNode<IRI> subquestions = questionTree;
			for(int j = 0; j < children.getLength(); j++) {
				Node curNode = children.item(j);
				if(curNode.getNodeName().equalsIgnoreCase("iri")) {
					IRI iri = getQuestionIRI(curNode, null);
					if(iri != null) {
						if(subquestions == null) subquestions = new TreeNode<IRI>(iri);
						else subquestions = subquestions.addChild(iri);
						checkAttributes(iri, qNode);
						questionList.add(iri);
					}
				}
				if(curNode.getNodeName().equalsIgnoreCase("questionlist")) // (sub-)<questionList>
					getQuestions(curNode, subquestions);
			}
			if(subquestions != null)
				questions.add(subquestions);
		}
		createQuestionList(questionListNode, questionList);
		return questions;
	}
	
	
	/**
	 * Create and add a QuestionList instance
	 * @param questionListNode	XML questionList node
	 * @param questionList	List of question IRIs in this node
	 */
	private void createQuestionList(Node questionListNode, List<IRI> questionList) {
		QuestionList ql = new QuestionList(questionList);
		String type = getAttribute(questionListNode, "type");
		String repeat = getAttribute(questionListNode, "repeat");
		int reps = 0;
		if(repeat != null && Integer.parseInt(repeat)>0)
			reps = Integer.parseInt(repeat);
		if(type != null && type.equals("inline"))
			if(reps > 0) {
				ql.setType(QuestionListType.INLINEREPEATED);
				ql.setRepetitions(reps);
			}
			else
				ql.setType(QuestionListType.INLINE);
		else if(reps > 0){
			ql.setType(QuestionListType.REPEATED);
			ql.setRepetitions(reps);
		}
		questionlists.add(ql);
	}
	
	
	/**
	 * Get the text content of the specified attribute within a given node, if such an attribute exists, otherwise this returns null
	 * @param node	Node
	 * @param attribute	Attribute name
	 * @return Text content of the given attribute in the specified node
	 */
	private String getAttribute(Node node, String attribute) {
		if(node.hasAttributes())
			if(node.getAttributes().getNamedItem(attribute) != null)
				return node.getAttributes().getNamedItem(attribute).getTextContent();
		return null;
	}
	
	
	/**
	 * Check attributes of a given question node
	 * @param iri	Question IRI
	 * @param qNode	Question node
	 */
	private void checkAttributes(IRI iri, Node qNode) {
		boolean numbered = true, required = false;
		if(qNode.hasAttributes()) {
			NamedNodeMap nodeMap = qNode.getAttributes();
			if(nodeMap.getNamedItem("numbered") != null)
				numbered = Boolean.parseBoolean(nodeMap.getNamedItem("numbered").getTextContent());
			if(nodeMap.getNamedItem("required") != null)
				required = Boolean.parseBoolean(nodeMap.getNamedItem("required").getTextContent());
			if(nodeMap.getNamedItem("showSubquestionsForAnswer") != null)
				subquestionPosTriggers.put(iri, IRI.create(nodeMap.getNamedItem("showSubquestionsForAnswer").getNodeValue()));
			if(nodeMap.getNamedItem("hideSubquestionsForAnswer") != null)
				subquestionNegTriggers.put(iri, IRI.create(nodeMap.getNamedItem("hideSubquestionsForAnswer").getNodeValue()));
			if(nodeMap.getNamedItem("optionOrder") != null) {
				String order = nodeMap.getNamedItem("optionOrder").getNodeValue();
				StringTokenizer tokenizer = new StringTokenizer(order, delim);
				List<String> list = new ArrayList<String>();
				while(tokenizer.hasMoreTokens())
					list.add(tokenizer.nextToken());
				optionsOrder.put(iri, list);
			}
		}
		questionNumbering.put(iri, numbered);
		questionRequired.put(iri, required);
	}
	
	
	/**
	 * Add subquestion (tree) trigger(s) given by the attribute node
	 * @param map	Map of question IRIs to lists of question IRIs which trigger subquestions
	 * @param iri	Question IRI
	 * @param n	Attribute node
	 */
	@SuppressWarnings("unused")
	private void addSubquestionTriggers(Map<IRI,List<IRI>> map, IRI iri, Node n) {
		String value = n.getNodeValue();
		List<IRI> list = new ArrayList<IRI>();
		if(value.contains(delim)) {
			StringTokenizer tokenizer = new StringTokenizer(value, delim);
			while(tokenizer.hasMoreTokens())
				list.add(IRI.create(tokenizer.nextToken()));
		}
		else
			list.add(IRI.create(value));
		
		if(map.containsKey(iri))
			list.addAll(map.get(iri));
		
		map.put(iri, list);
	}
	
	
	/**
	 * Get information requests (e.g., information such as name, id, etc) 
	 * @param n	Infolist node
	 * @return List of IRIs
	 */
	private List<TreeNode<IRI>> getInfoRequests(Node n) {
		List<TreeNode<IRI>> inforeqs = new ArrayList<TreeNode<IRI>>();
		NodeList nl = n.getChildNodes(); // <info>'s
		for(int i = 0; i < nl.getLength(); i++) {
			Node child = nl.item(i);
			boolean required = false;
			IRI iri = null;
			TreeNode<IRI> info = null;
			if(child.hasAttributes()) {
				NamedNodeMap nodemap = child.getAttributes();
				for(int j = 0; j < nodemap.getLength(); j++) {
					Node att = nodemap.item(j);
					if(att.getNodeName().equals("property")) {
						String prop = att.getNodeValue();
						iri = getQuestionIRI(doc.getElementById(prop), child);
						questionNumbering.put(iri, required);
						info = new TreeNode<IRI>(iri);
					}
					else if(att.getNodeName().equals("required"))
						required = Boolean.parseBoolean(att.getNodeValue());
				}
			}
			if(info != null) {
				inforeqs.add(info);
				questionRequired.put(iri, required);
			}
		}
		return inforeqs;
	}
	
	
	/**
	 * Get IRI of question at the given node
	 * @param node	Current node
	 * @param eleNode	Information element node, if applicable
	 * @return IRI of question in given node
	 */
	private IRI getQuestionIRI(Node node, Node eleNode) {
		String iriTxt = node.getTextContent();
		IRI iri = null;
		if(!iriTxt.equals("")) {
			iri = IRI.create(iriTxt);
			if(iri != null && verbose)
				System.out.print("\tQuestion: " + iriTxt);
			if(node.hasAttributes())
				checkQuestionType(iri, node);
			if(node.getParentNode().hasAttributes())
				checkQuestionType(iri, node.getParentNode());
			if(eleNode != null)
				checkQuestionType(iri, eleNode);
			if(iri != null && verbose) 
				System.out.println();
		}
		return iri;
	}

	
	/**
	 * Get the type of question given as an attribute
	 * @param iri	IRI of the question individual
	 * @param curNode	Current node being checked
	 */
	private void checkQuestionType(IRI iri, Node curNode) {
		Node n = curNode.getAttributes().getNamedItem("type");
		if(n != null) {
			ElementType qType = null;
			String type = n.getNodeValue();
			for(int i = 0; i < ElementType.values().length; i++) {
				if(type.equalsIgnoreCase(ElementType.values()[i].toString()))
					qType = ElementType.values()[i];
			}
			if(verbose) System.out.print(" (type: " + qType + ")");
			elementTypes.put(iri, qType);
		}
	}
	
	
	/*	QUESTION TYPES	*/
	
	
	/**
	 * Check if the configuration file contains a question type for the given question
	 * @param i	IRI of the question
	 * @return true if question type is defined in the configuration file, false otherwise
	 */
	public boolean hasDefinedType(IRI i) {
		if(elementTypes.containsKey(i))
			return true;
		else
			return false;
	}
	
	
	/**
	 * Get the question type for the given question IRI
	 * @param i	IRI of individual representing a question
	 * @return Question type
	 */
	public ElementType getQuestionType(IRI i) {
		return elementTypes.get(i);
	}
	
	
	/**
	 * Get the map of question IRIs to their respective type as defined in the configuration file
	 * @return Map of IRIs to question types
	 */
	public Map<IRI,ElementType> getQuestionTypes() {
		return elementTypes;
	}
	
	
	/*	SECTION TYPES	*/
	
	/**
	 * Get the section type for the given section IRI
	 * @param i	Section IRI
	 * @return Section type
	 */
	public SectionType getSectionType(IRI i) {
		return sectionTypes.get(i);
	}
	
	
	/**
	 * Get the map of section IRIs to their respective type as defined in the configuration file
	 * @return Map of IRIs to section types
	 */
	public Map<IRI,SectionType> getSectionTypes() {
		return sectionTypes;
	}
	
	
	/*	SECTION - QUESTION - NUMBERING MAP	*/
	
	
	/**
	 * Get the map of sections and their corresponding questions specified in the configuration file
	 * @return Map of sections' IRIs to the questions they contain 
	 */
	public Map<IRI,List<TreeNode<IRI>>> getSectionMap() {
		return sections;
	}
	
	
	/**
	 * Get the map of sections and whether they (and their components) should or should not be numbered
	 * @return Map of sections' IRIs to whether they are numbered or not
	 */
	public Map<IRI,Boolean> getSectionNumberingMap() {
		return sectionNumbering;
	}
	
	
	/**
	 * Get the map of questions and whether they should or should not be numbered
	 * @return Map of questions' IRIs to whether they are numbered or not
	 */
	public Map<IRI,Boolean> getQuestionNumberingMap() {
		return questionNumbering;
	}
	
	
	/**
	 * Get the map of question IRIs to whether they are required input
	 * @return Map of questions' IRIs to whether they are required or not
	 */
	public Map<IRI,Boolean> getQuestionRequiredMap() {
		return questionRequired;
	}
	
	
	/*	INPUT AND OUTPUT	*/
	
	
	/**
	 * Get file path to main ontology input
	 * @return File path
	 */
	public String getInputOntologyPath() {
		return ontPath;
	}
	
	
	/**
	 * Get the map of imported ontologies' IRIs and file paths
	 * @return Map of IRIs and file paths of imported ontologies
	 */
	public Map<IRI,String> getInputImportsMap() {
		return imports;
	}
	
	
	/**
	 * Get the file path of the output file
	 * @return String with the file path of the output file
	 */
	public String getOutputFilePath() {
		return outPath;
	}

	
	/**
	 * Get the title of the (HTML) output file
	 * @return String describing the title of the output file
	 */
	public String getOutputFileTitle() {
		return title;
	}
	
	
	/**
	 * Get the CSS style class to be used in the form output
	 * @return String specifying the output CSS class
	 */
	public String getCSSStyleClass() {
		return cssStyle;
	}

	
	/*	CLASS BINDINGS	*/
	
	
	/**
	 * Get the OWL class IRI which represents the output type, i.e., all answers will be instances of this class 
	 * @return OWL class IRI
	 */
	public IRI getOutputClass() {
		String binding = "data";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}	
	
	
	/**
	 * Get the IRI of the OWL class for data element values 
	 * @return OWL class IRI
	 */
	public IRI getDataElementValueClassBinding() {
		String binding = "data_element";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the IRI of the OWL class for form data
	 * @return OWL class IRI
	 */
	public IRI getFormDataClassBinding() {
		String binding = "form_data";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the IRI of the OWL class bound to question sections. Each question data will be an instance of this class
	 * @return OWL class IRI
	 */
	public IRI getQuestionSectionClassBinding() {
		String binding = "question_section";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the IRI of the OWL class bound to the initial section(s). The data collected in an "initial section" will form an instance of this class
	 * @return OWL class IRI
	 */
	public IRI getInitialSectionClassBinding() {
		String binding = "patient_section";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the IRI of the OWL class bound to the final section(s). The data collected in a "final section" will form an instance of this class
	 * @return OWL class IRI
	 */
	public IRI getFinalSectionClassBinding() {
		String binding = "physician_section";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the IRI of the OWL class that represents the physician certification
	 * @return OWL class IRI
	 */
	public IRI getPhysicianCertificationClassBinding() {
		String binding = "physician_cert";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for class '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/*	PROPERTY BINDINGS	*/
	
	
	/**
	 * Get the property IRI which represents the questions' text
	 * @return OWL data property IRI
	 */
	public IRI getQuestionTextPropertyBinding() {
		String binding = "questiontext";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	

	/**
	 * Get the property IRI which represents the questions' focus
	 * @return OWL data property IRI
	 */
	public IRI getQuestionFocusPropertyBinding() {
		String binding = "questionfocus";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the property IRI which represents the questions' possible value(s)
	 * @return OWL object property IRI
	 */
	public IRI getQuestionValuePropertyBinding() {
		String binding = "questionvalue";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the data property IRI which is used to represent the numeric value of a question's option
	 * @return OWL data property IRI
	 */
	public IRI getQuestionDataValuePropertyBinding() {
		String binding = "questiondatavalue";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL data property IRI that gives a heading value to a section instance
	 * @return OWL data property IRI
	 */
	public IRI getSectionHeaderPropertyBinding() {
		String binding = "sectionheading";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL data property IRI that is used to specify the text at the beginning of a section
	 * @return OWL data property IRI
	 */
	public IRI getSectionTextPropertyBinding() {
		String binding = "sectiontext";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property'" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL object property IRI for 'isAnswerTo'
	 * @return OWL object property IRI
	 */
	public IRI getIsAnswerToPropertyBinding() {
		String binding = "is_answer_to";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasAnswer'
	 * @return OWL object property IRI
	 */
	public IRI getHasAnswerPropertyBinding() {
		String binding = "has_answer";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasForm'
	 * @return OWL object property IRI
	 */
	public IRI getHasFormPropertyBinding() {
		String binding = "has_form";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasMember'
	 * @return OWL object property IRI
	 */
	public IRI getHasComponentPropertyBinding() {
		String binding = "has_component";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasDate'
	 * @return OWL object property IRI
	 */
	public IRI getHasDatePropertyBinding() {
		String binding = "has_date";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for property '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/*	QUESTION INPUT TYPES (HTML FORM)	*/
	
	
	/**
	 * Get the OWL class IRI which represents an HTML radio input element
	 * @return OWL class IRI
	 */
	public IRI getRadioInputBinding() {
		String binding = "radio";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for question type '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL class IRI which represents an HTML text field input element
	 * @return OWL class IRI
	 */
	public IRI getTextInputBinding() {
		String binding = "textarea";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for question type '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL individual IRI which represents the boolean value for true
	 * @return OWL individual IRI
	 */
	public IRI getBooleanTrueValueBinding() {
		String binding = "bool_true";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for boolean value '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL individual IRI which represents the boolean value for false
	 * @return OWL individual IRI
	 */
	public IRI getBooleanFalseValueBinding() {
		String binding = "bool_false";
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for boolean value '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/**
	 * Get the OWL individual IRI which represents the form
	 * @return OWL individual IRI
	 */
	public IRI getFormIndividualIRI() {
		return IRI.create(formIri);
	}
	
	
	/*	PRINT SECTION / QUESTION MAP	*/

	
	/**
	 * Print a given map of sections
	 * @param sections	Map of sections
	 */
	@SuppressWarnings("unused")
	private void print(Map<IRI,List<TreeNode<IRI>>> sections) {
		int counter = 0;
		for(IRI iri : sections.keySet()) {
			System.out.println("Section " + counter + ": " + iri.getShortForm());
			List<TreeNode<IRI>> questions = sections.get(iri);
			print(questions);
			counter++;
		}
	}
	
	
	/**
	 * Print a list of questions
	 * @param questions	List of question treenodes
	 */
	private void print(List<TreeNode<IRI>> questions) {
		for(int i = 0; i < questions.size(); i++) {
			TreeNode<IRI> question = questions.get(i);
			print(question);
		}
	}
	
	
	/**
	 * Print the information in a question treenode
	 * @param treenode	Treenode
	 */
	private void print(TreeNode<IRI> treenode) {
		Iterator<TreeNode<IRI>> iter = treenode.iterator();
		TreeNode<IRI> t = null;
		while(iter.hasNext()) {
			t = iter.next();
			for(int j = 0; j <= t.getLevel(); j++) 
				System.out.print("    ");
			System.out.println("(" + t.getLevel() + ") Question: " + t.data.getShortForm());
		}
	}
	
	
	/*	SUBQUESTION TRIGGERS	*/
	
	
	/**
	 * Get the map of question IRI's to the answer(s) that, when chosen, will cause subquestions to appear
	 * @return Map of question IRI's to answer(s)' IRI's whose choice causes subquestions to appear
	 */
	public Map<IRI,IRI> getSubquestionPositiveTriggers() {
		return subquestionPosTriggers;
	}
	
	
	/**
	 * Get the map of question IRI's to the answer(s) that, when chosen, will cause subquestions to disappear
	 * @return Map of question IRI's to answer(s)' IRI's whose choice causes subquestions to disappear
	 */
	public Map<IRI,IRI> getSubquestionNegativeTriggers() {
		return subquestionNegTriggers;
	}

	
	/*	MISC	*/
	
	
	/**
	 * Get the map of question IRI's to the list which determines the order in which question options will 
	 * be presented in the HTML form
	 * @return Map of question IRI's to a list of strings
	 */
	public Map<IRI,List<String>> getOptionsOrderMap() {
		return optionsOrder;
	}
	

	/**
	 * Get the list of question lists parsed in this configuration
	 * @return List of question list objects
	 */
	public List<QuestionList> getQuestionLists() {
		return questionlists;
	}
}