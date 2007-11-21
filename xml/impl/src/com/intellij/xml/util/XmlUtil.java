package com.intellij.xml.util;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.JDOMXIncluder;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Mike
 */
public class XmlUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.XmlUtil");

  @NonNls public static final String TAGLIB_1_2_URI = "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd";

  @NonNls public static final String XML_SCHEMA_URI = "http://www.w3.org/2001/XMLSchema";
  @NonNls public static final String XML_SCHEMA_URI2 = "http://www.w3.org/1999/XMLSchema";
  @NonNls public static final String XML_SCHEMA_URI3 = "http://www.w3.org/2000/10/XMLSchema";
  @NonNls public static final String XML_SCHEMA_INSTANCE_URI = "http://www.w3.org/2001/XMLSchema-instance";

  @NonNls public static final String XSLT_URI = "http://www.w3.org/1999/XSL/Transform";
  @NonNls public static final String XINCLUDE_URI = "http://www.w3.org/2001/XInclude";

  @NonNls public static final String ANT_URI = "http://ant.apache.org/schema.xsd";
  @NonNls public static final String XHTML_URI = "http://www.w3.org/1999/xhtml";
  @NonNls public static final String HTML_URI = "http://www.w3.org/1999/html";
  @NonNls public static final String EMPTY_URI = "";
  @NonNls public static final Key<String> TEST_PATH = Key.create("TEST PATH");
  @NonNls public static final String JSP_URI = "http://java.sun.com/JSP/Page";
  @NonNls public static final String ANY_URI = "http://www.intellij.net/ns/any";

  @NonNls private static final String JSTL_CORE_URI = "http://java.sun.com/jsp/jstl/core";
  @NonNls private static final String JSTL_CORE_URI2 = "http://java.sun.com/jstl/core";
  @NonNls private static final String JSTL_CORE_URI3 = "http://java.sun.com/jstl/core_rt";
  @NonNls public static final String[] JSTL_CORE_URIS = {JSTL_CORE_URI, JSTL_CORE_URI2, JSTL_CORE_URI3};

  @NonNls public static final String JSF_HTML_URI = "http://java.sun.com/jsf/html";
  @NonNls public static final String JSF_CORE_URI = "http://java.sun.com/jsf/core";

  @NonNls private static final String JSTL_FORMAT_URI = "http://java.sun.com/jsp/jstl/fmt";
  @NonNls private static final String JSTL_FORMAT_URI2 = "http://java.sun.com/jstl/fmt";
  @NonNls private static final String JSTL_FORMAT_URI3 = "http://java.sun.com/jstl/fmt_rt";
  @NonNls public static final String[] JSTL_FORMAT_URIS = {JSTL_FORMAT_URI, JSTL_FORMAT_URI2, JSTL_FORMAT_URI3};

  @NonNls public static final String SPRING_URI = "http://www.springframework.org/tags";
  @NonNls public static final String SPRING_FORMS_URI = "http://www.springframework.org/tags/form";
  @NonNls public static final String STRUTS_BEAN_URI = "http://struts.apache.org/tags-bean";
  @NonNls public static final String STRUTS_BEAN_URI2 = "http://jakarta.apache.org/struts/tags-bean";
  @NonNls public static final String APACHE_I18N_URI = "http://jakarta.apache.org/taglibs/i18n-1.0";
  @NonNls public static final String STRUTS_LOGIC_URI = "http://struts.apache.org/tags-logic";
  @NonNls public static final String STRUTS_HTML_URI = "http://struts.apache.org/tags-html";
  @NonNls public static final String STRUTS_HTML_URI2 = "http://jakarta.apache.org/struts/tags-html";

  @NonNls public static final String APACHE_TRINIDAD_URI = "http://myfaces.apache.org/trinidad";
  @NonNls public static final String APACHE_TRINIDAD_HTML_URI = "http://myfaces.apache.org/trinidad/html";


  @NonNls public static final String[] HIBERNATE_URIS =
    {"http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd", "http://hibernate.sourceforge.net/hibernate-mapping-2.0.dtd"};

  @NonNls public static final String XSD_SIMPLE_CONTENT_TAG = "simpleContent";
  public static final @NonNls String NO_NAMESPACE_SCHEMA_LOCATION_ATT = "noNamespaceSchemaLocation";
  public static final @NonNls String SCHEMA_LOCATION_ATT = "schemaLocation";
  public static final @NonNls String[] WEB_XML_URIS = {"http://java.sun.com/xml/ns/j2ee", "http://java.sun.com/xml/ns/javaee",
    "http://java.sun.com/dtd/web-app_2_3.dtd", "http://java.sun.com/j2ee/dtds/web-app_2_2.dtd"};
  public static final @NonNls String FACELETS_URI = "http://java.sun.com/jsf/facelets";
  public static final @NonNls String JSTL_FUNCTIONS_URI = "http://java.sun.com/jsp/jstl/functions";
  public static final @NonNls String JSTL_FN_FACELET_URI = "com.sun.facelets.tag.jstl.fn.JstlFnLibrary";
  public static final @NonNls String JSTL_CORE_FACELET_URI = "com.sun.facelets.tag.jstl.core.JstlCoreLibrary";
  @NonNls public static final String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";
  public static final @NonNls String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
  public static final List<String> ourSchemaUrisList = Arrays.asList(MetaRegistry.SCHEMA_URIS);


  private XmlUtil() {
  }

  public static String getSchemaLocation(XmlTag tag, String namespace) {
    final String uri = ExternalResourceManagerEx.getInstanceEx().getResourceLocation(namespace);
    if (uri != null && !uri.equals(namespace)) return uri;

    while (true) {
      if ("".equals(namespace)) {
        final String attributeValue = tag.getAttributeValue("noNamespaceSchemaLocation", XML_SCHEMA_INSTANCE_URI);
        if (attributeValue != null) return attributeValue;
      }
      else {
        String schemaLocation = tag.getAttributeValue("schemaLocation", XML_SCHEMA_INSTANCE_URI);
        if (schemaLocation != null) {
          int start = schemaLocation.indexOf(namespace);
          if (start >= 0) {
            start += namespace.length();
            final StringTokenizer tokenizer = new StringTokenizer(schemaLocation.substring(start + 1));
            if (tokenizer.hasMoreTokens()) {
              return tokenizer.nextToken();
            }
            else {
              return null;
            }
          }
        }
      }
      if (tag.getParent() instanceof XmlTag) {
        tag = (XmlTag)tag.getParent();
      }
      else {
        break;
      }
    }
    return null;
  }

  @Nullable
  public static String findNamespacePrefixByURI(XmlFile file, @NonNls String uri) {
    if (file == null) return null;
    final XmlDocument document = file.getDocument();
    if (document == null) return null;
    final XmlTag tag = document.getRootTag();
    if (tag == null) return null;

    for (XmlAttribute attribute : tag.getAttributes()) {
      if (attribute.getName().startsWith("xmlns:") && attribute.getValue().equals(uri)) {
        return attribute.getName().substring("xmlns:".length());
      }
      if ("xmlns".equals(attribute.getName()) && attribute.getValue().equals(uri)) return "";
    }

    return null;
  }

  public static String[] findNamespacesByURI(XmlFile file, String uri) {
    if (file == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final XmlDocument document = file.getDocument();
    if (document == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final XmlTag tag = document.getRootTag();
    if (tag == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    XmlAttribute[] attributes = tag.getAttributes();


    List<String> result = new ArrayList<String>();

    for (XmlAttribute attribute : attributes) {
      if (attribute.getName().startsWith("xmlns:") && attribute.getValue().equals(uri)) {
        result.add(attribute.getName().substring("xmlns:".length()));
      }
      if ("xmlns".equals(attribute.getName()) && attribute.getValue().equals(uri)) result.add("");
    }

    return result.toArray(new String[result.size()]);
  }

  public static String getXsiNamespace(XmlFile file) {
    return findNamespacePrefixByURI(file, XML_SCHEMA_INSTANCE_URI);
  }

  private static final Key<String> findXmlFileInProgressKey = Key.create("find.xml.file.in.progress");

  public static XmlFile findNamespace(PsiFile base, @NotNull String uri) {
    final XmlFile xmlFile = XmlSchemaProvider.findSchema(uri, base);
    return xmlFile == null ? findXmlFile(base, uri) : xmlFile;
  }

  public static XmlFile findXmlFile(PsiFile base, @NotNull String uri) {
    PsiFile result = null;
    final JspFile jspFile = PsiUtil.getJspFile(base);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String data = jspFile != null ? jspFile.getUserData(TEST_PATH) : base.getUserData(TEST_PATH);

      if (data == null) {
        PsiFile originalFile = base.getOriginalFile();
        if (originalFile != null) {
          data = originalFile.getUserData(TEST_PATH);
        }
      }
      if (data != null) {
        String filePath = data + "/" + uri;
        final VirtualFile path = LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
        if (path != null) {
          result = base.getManager().findFile(path);
        }
      }
    }
    if (result == null) {
      result = PsiUtil.findRelativeFile(uri, base);
    }

    if (result == null || !(result instanceof XmlFile)) {
      final JspManager jspManager = JspManager.getInstance(base.getProject());
      if (jspManager != null) {
        if (jspFile != null) {
          result = jspManager.getTldFileByUri(uri, jspFile);
          if (result == null && JSTL_CORE_URI2.equals(uri)) {
            result = jspManager.getTldFileByUri(JSTL_CORE_URI, jspFile);
          }
        }
        else {
          // check facelets file
          if (base instanceof XmlFile && base.getUserData(findXmlFileInProgressKey) == null) {
            base.putUserData(findXmlFileInProgressKey, "");
            try {
              final XmlDocument document = ((XmlFile)base).getDocument();
              final XmlTag rootTag = document != null ? document.getRootTag() : null;

              if (rootTag != null && rootTag.getPrefixByNamespace(FACELETS_URI) != null) {
                result = jspManager.getTldFileByUri(uri, ModuleUtil.findModuleForPsiElement(base), null);
              }
            }
            finally {
              base.putUserData(findXmlFileInProgressKey, null);
            }
          }
        }
      }
    }

    if (result instanceof XmlFile) {
      return (XmlFile)result;
    }

    return null;
  }

  public static XmlToken getTokenOfType(PsiElement element, IElementType type) {
    if (element == null) {
      return null;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      if (child instanceof XmlToken) {
        XmlToken token = (XmlToken)child;

        if (token.getTokenType() == type) {
          return token;
        }
      }
    }

    return null;
  }

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag) {
    return processXmlElements(element, processor, deepFlag, false);
  }

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag, boolean wideFlag) {
    if (element == null) return true;
    PsiFile baseFile = element.isValid() ? element.getContainingFile() : null;
    return processXmlElements(element, processor, deepFlag, wideFlag, baseFile);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile) {
    final PsiIncludeManager includeManager =
      (PsiIncludeManager)element.getProject().getPicoContainer().getComponentInstanceOfType(PsiIncludeManager.class);
    return new XmlElementProcessor(processor, baseFile, includeManager).processXmlElements(element, deepFlag, wideFlag);
  }

  public static boolean processXmlElementChildren(final XmlElement element, final PsiElementProcessor processor, final boolean deepFlag) {
    final PsiIncludeManager includeManager =
      (PsiIncludeManager)element.getProject().getPicoContainer().getComponentInstanceOfType(PsiIncludeManager.class);
    final XmlElementProcessor p = new XmlElementProcessor(processor, element.getContainingFile(), includeManager);

    final boolean wideFlag = false;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!p.processElement(child, deepFlag, wideFlag) && !wideFlag) return false;
    }

    return true;
  }

  public static ParserDefinition.SpaceRequirements canStickTokensTogetherByLexerInXml(final ASTNode left,
                                                                                      final ASTNode right,
                                                                                      final Lexer lexer,
                                                                                      int state) {
    if (left.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN || right.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      return ParserDefinition.SpaceRequirements.MUST_NOT;
    }
    if (left.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER && right.getElementType() == XmlTokenType.XML_NAME) {
      return ParserDefinition.SpaceRequirements.MUST;
    }
    if (left.getElementType() == XmlTokenType.XML_NAME && right.getElementType() == XmlTokenType.XML_NAME) {
      return ParserDefinition.SpaceRequirements.MUST;
    }
    return ParserDefinition.SpaceRequirements.MAY;
  }

  public static boolean tagFromTemplateFramework(@NotNull final XmlTag tag) {
    final String ns = tag.getNamespace();
    return nsFromTemplateFramework(ns);
  }

  public static boolean nsFromTemplateFramework(final String ns) {
    return XSLT_URI.equals(ns) || XINCLUDE_URI.equals(ns);
  }


  public static char getCharFromEntityRef(@NonNls String text) {
    //LOG.assertTrue(text.startsWith("&#") && text.endsWith(";"));
    if (text.charAt(1) != '#') {
      text = text.substring(1, text.length() - 1);
      return XmlTagTextUtil.getCharacterByEntityName(text).charValue();
    }
    text = text.substring(2, text.length() - 1);
    int code;
    if (StringUtil.startsWithChar(text, 'x')) {
      text = text.substring(1);
      code = Integer.parseInt(text, 16);
    }
    else {
      code = Integer.parseInt(text);
    }
    return (char)code;
  }

  public static boolean attributeFromTemplateFramework(@NonNls final String name, final XmlTag tag) {
    return "jsfc".equals(name) && tag.getNSDescriptor(JSF_HTML_URI, true) != null;
  }

  public static
  @Nullable
  String getTargetSchemaNsFromTag(@Nullable final XmlTag xmlTag) {
    if (xmlTag == null) return null;
    String targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI);
    if (targetNamespace == null) targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI2);
    if (targetNamespace == null) targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI3);
    return targetNamespace;
  }

  @Nullable
  public static XmlTag getSchemaSimpleContent(@NotNull XmlTag tag) {
    XmlElementDescriptor descriptor = tag.getDescriptor();

    if (descriptor instanceof XmlElementDescriptorImpl) {
      final TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType(tag);

      if (type instanceof ComplexTypeDescriptor) {
        final XmlTag[] simpleContent = new XmlTag[1];

        processXmlElements(((ComplexTypeDescriptor)type).getDeclaration(), new PsiElementProcessor() {
          public boolean execute(final PsiElement element) {
            if (element instanceof XmlTag &&
                ((XmlTag)element).getLocalName().equals(XSD_SIMPLE_CONTENT_TAG) &&
                ((XmlTag)element).getNamespace().equals(XML_SCHEMA_URI)) {
              simpleContent[0] = (XmlTag)element;
              return false;
            }

            return true;
          }
        }, true);

        return simpleContent[0];
      }
    }
    return null;
  }

  public static <T extends PsiElement> void doDuplicationCheckForElements(final T[] elements,
                                                                          final HashMap<String, T> presentNames,
                                                                          DuplicationInfoProvider<T> provider,
                                                                          final Validator.ValidationHost host) {
    for (T t : elements) {
      final String name = provider.getName(t);
      if (name == null) continue;

      final String nameKey = provider.getNameKey(t, name);

      if (presentNames.containsKey(nameKey)) {
        final T psiElement = presentNames.get(nameKey);
        final String message = XmlBundle.message("duplicated.declaration", nameKey);

        if (psiElement != null) {
          presentNames.put(nameKey, null);

          host.addMessage(provider.getNodeForMessage(psiElement), message, Validator.ValidationHost.ERROR);
        }

        host.addMessage(provider.getNodeForMessage(t), message, Validator.ValidationHost.ERROR);
      }
      else {
        presentNames.put(nameKey, t);
      }
    }
  }

  public static String getEntityValue(final XmlEntityRef entityRef) {
    final XmlEntityDecl decl = entityRef.resolve(entityRef.getContainingFile());
    if (decl != null) {
      final XmlAttributeValue valueElement = decl.getValueElement();
      if (valueElement != null) {
        final String value = valueElement.getValue();
        if (value != null) {
          return value;
        }
      }
    }
    return entityRef.getText();
  }

  private static class XmlElementProcessor {
    private PsiElementProcessor processor;
    private PsiFile targetFile;
    private final PsiIncludeManager myIncludeManager;
    private static final Key<CachedValue<PsiElement[]>> KEY_RESOLVED_XINCLUDE = Key.create("RESOLVED_XINCLUDE");

    XmlElementProcessor(PsiElementProcessor _processor, PsiFile _targetFile, PsiIncludeManager includeManager) {
      processor = _processor;
      targetFile = _targetFile;
      myIncludeManager = includeManager;
    }

    private boolean processXmlElements(PsiElement element, boolean deepFlag, boolean wideFlag) {
      if (deepFlag) if (!processor.execute(element)) return false;

      PsiElement startFrom = element.getFirstChild();

      if (element instanceof XmlEntityRef) {
        XmlEntityRef ref = (XmlEntityRef)element;

        //if ("Number.datatype".equals(ref.getText().substring(1, ref.getTextLength() - 1))) {
        //  int a = 1;
        //}
        PsiElement newElement = parseEntityRef(targetFile, ref, true);
        //if (newElement == null) {
        //  System.out.println("No image for :" + ref.getText());
        //  return true;
        //}

        while (newElement != null) {
          if (!processElement(newElement, deepFlag, wideFlag)) return false;
          newElement = newElement.getNextSibling();
        }

        return true;
      }
      else if (element instanceof XmlConditionalSection) {
        XmlConditionalSection xmlConditionalSection = (XmlConditionalSection)element;
        if (!xmlConditionalSection.isIncluded(targetFile)) return true;
        startFrom = xmlConditionalSection.getBodyStart();
      }
      else if (XmlIncludeHandler.isXInclude(element)) {
        XmlTag tag = (XmlTag)element;

        myIncludeManager.includeProcessed(tag);
        if (!processXInclude(deepFlag, wideFlag, tag)) return false;
      }

      for (PsiElement child = startFrom; child != null; child = child.getNextSibling()) {
        if (!processElement(child, deepFlag, wideFlag) && !wideFlag) return false;
      }

      return true;
    }

    private boolean processXInclude(final boolean deepFlag, final boolean wideFlag, final XmlTag xincludeTag) {
      final PsiElement[] inclusion = xincludeTag.getManager().getCachedValuesManager()
        .getCachedValue(xincludeTag, KEY_RESOLVED_XINCLUDE, new Function<XmlTag, CachedValueProvider<PsiElement[]>>() {
          public CachedValueProvider<PsiElement[]> fun(final XmlTag xmlTag) {
            return new CachedValueProvider<PsiElement[]>() {
              public Result<PsiElement[]> compute() {

                //System.err.println("************ recomputing xInclude:" + xincludeTag.getText() + " : " + System.identityHashCode(this));
                //Thread.dumpStack();
                //
                PsiElement[] result = null;
                List<Object> deps = new ArrayList<Object>();
                deps.add(xincludeTag.getContainingFile());

                final XmlFile xmlFile = XmlIncludeHandler.resolveXIncludeFile(xincludeTag);

                if (xmlFile != null) {
                  deps.add(xmlFile);
                  final XmlDocument document = xmlFile.getDocument();
                  if (document != null) {
                    XmlTag rootTag = document.getRootTag();
                    if (rootTag != null) {
                      final XmlTag[] includeTag = extractXpointer(rootTag, xincludeTag);
                      result = ContainerUtil.map(includeTag, new Function<XmlTag, PsiElement>() {
                        public PsiElement fun(final XmlTag xmlTag) {
                          final PsiElement psiElement = xmlTag.copy();
                          psiElement.putUserData(XmlElement.ORIGINAL_ELEMENT, xincludeTag.getParentTag());
                          return psiElement;
                        }
                      }, new PsiElement[includeTag.length]);
                    }
                  }
                }

                return new Result<PsiElement[]>(result, deps.toArray());
              }
            };
          }
        }, false);

      if (inclusion != null) {
        for (PsiElement psiElement : inclusion) {
          if (!processElement(psiElement, deepFlag, wideFlag)) return false;
        }
      }

      return true;
    }

    private static XmlTag[] extractXpointer(XmlTag rootTag, final XmlTag xincludeTag) {
      final String xpointer = xincludeTag.getAttributeValue("xpointer", XINCLUDE_URI);

      if (xpointer != null) {
        Matcher matcher = JDOMXIncluder.XPOINTER_PATTERN.matcher(xpointer);
        if (matcher.matches()) {
          String pointer = matcher.group(1);
          matcher = JDOMXIncluder.CHILDREN_PATTERN.matcher(pointer);

          if (matcher.matches()) {
            final String rootTagName = matcher.group(1);

            if (rootTagName.equals(rootTag.getName())) return rootTag.getSubTags();
          }
        }
      }

      return new XmlTag[]{rootTag};
    }

    private boolean processElement(PsiElement child, boolean deepFlag, boolean wideFlag) {
      if (deepFlag) {
        if (!processXmlElements(child, true, wideFlag)) {
          return false;
        }
      }
      else {
        if (child instanceof XmlEntityRef) {
          if (!processXmlElements(child, false, wideFlag)) return false;
        }
        else if (child instanceof XmlConditionalSection) {
          if (!processXmlElements(child, false, wideFlag)) return false;
        }
        else if (XmlIncludeHandler.isXInclude(child)) {
          if (!processXmlElements(child, false, wideFlag)) return false;
        }
        else if (!processor.execute(child)) return false;
      }
      if (child instanceof XmlEntityDecl) {
        XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)child;
        XmlEntityRefImpl.cacheParticularEntity(targetFile, xmlEntityDecl);
      }
      return true;
    }

  }

  private static PsiElement parseEntityRef(PsiFile targetFile, XmlEntityRef ref, boolean cacheValue) {
    int type = getContextType(ref);

    {
      final XmlEntityDecl entityDecl = ref.resolve(targetFile);
      if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
    }

    PsiElement e = ref;
    while (e != null) {
      if (e.getUserData(XmlElement.ORIGINAL_ELEMENT) != null) {
        e = e.getUserData(XmlElement.ORIGINAL_ELEMENT);
        final PsiFile f = e.getContainingFile();
        if (f != null) {
          final XmlEntityDecl entityDecl = ref.resolve(targetFile);
          if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
        }

        continue;
      }
      if (e instanceof PsiFile) {
        PsiFile refFile = (PsiFile)e;
        final XmlEntityDecl entityDecl = ref.resolve(refFile);
        if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
        break;
      }

      e = e.getParent();
    }

    final PsiElement element = ref.getUserData(XmlElement.DEPENDING_ELEMENT);
    if (element instanceof XmlFile) {
      final XmlEntityDecl entityDecl = ref.resolve((PsiFile)element);
      if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
    }

    return null;
  }

  private static int getContextType(XmlEntityRef ref) {
    int type = XmlEntityDecl.CONTEXT_GENERIC_XML;
    PsiElement temp = ref;
    while (temp != null) {
      if (temp instanceof XmlAttributeDecl) {
        type = XmlEntityDecl.CONTEXT_ATTRIBUTE_SPEC;
      }
      else if (temp instanceof XmlElementDecl) {
        type = XmlEntityDecl.CONTEXT_ELEMENT_CONTENT_SPEC;
      }
      else if (temp instanceof XmlAttlistDecl) {
        type = XmlEntityDecl.CONTEXT_ATTLIST_SPEC;
      }
      else if (temp instanceof XmlEntityDecl) {
        type = XmlEntityDecl.CONTEXT_ENTITY_DECL_CONTENT;
      }
      else if (temp instanceof XmlEnumeratedType) {
        type = XmlEntityDecl.CONTEXT_ENUMERATED_TYPE;
      }
      else {
        temp = temp.getContext();
        continue;
      }
      break;
    }
    return type;
  }

  private static final Key<CachedValue<PsiElement>> PARSED_DECL_KEY = Key.create("PARSED_DECL_KEY");

  private static PsiElement parseEntityDecl(final XmlEntityDecl entityDecl,
                                            final PsiFile targetFile,
                                            final int type,
                                            boolean cacheValue,
                                            final XmlEntityRef entityRef) {
    if (!cacheValue) return entityDecl.parse(targetFile, type, entityRef);

    synchronized (PsiLock.LOCK) { // we depend on targetFile and entityRef
      CachedValue<PsiElement> value = entityRef.getUserData(PARSED_DECL_KEY);
      //    return entityDecl.parse(targetFile, type);

      if (value == null) {
        value = entityDecl.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<PsiElement>() {
          public CachedValueProvider.Result<PsiElement> compute() {
            final PsiElement res = entityDecl.parse(targetFile, type, entityRef);
            if (res == null) return new Result<PsiElement>(res, targetFile);
            if (!entityDecl.isInternalReference()) XmlEntityRefImpl.copyEntityCaches(res.getContainingFile(), targetFile);
            return new CachedValueProvider.Result<PsiElement>(res, res.getUserData(XmlElement.DEPENDING_ELEMENT), entityDecl, targetFile,
                                                              entityRef);
          }
        }, false);
        entityRef.putUserData(PARSED_DECL_KEY, value);
      }

      return value.getValue();
    }
  }

  /**
   * add child to the parent according to DTD/Schema element ordering
   *
   * @return newly added child
   */
  public static XmlTag addChildTag(XmlTag parent, XmlTag child) throws IncorrectOperationException {
    return addChildTag(parent, child, -1);
  }

  public static XmlTag addChildTag(XmlTag parent, XmlTag child, int index) throws IncorrectOperationException {

    // bug in PSI: cannot add child to <tag/>
    if (parent.getSubTags().length == 0 && parent.getText().endsWith("/>")) {
      final PsiElementFactory factory = parent.getManager().getElementFactory();
      final String name = parent.getName();
      final String text = parent.getText();
      final XmlTag tag = factory.createTagFromText(text.substring(0, text.length() - 2) + "></" + name + ">");
      parent = (XmlTag)parent.replace(tag);
    }

    final XmlElementDescriptor parentDescriptor = parent.getDescriptor();
    final XmlTag[] subTags = parent.getSubTags();
    if (parentDescriptor == null || subTags.length == 0) return (XmlTag)parent.add(child);
    int subTagNum = -1;

    for (XmlElementDescriptor childElementDescriptor : parentDescriptor.getElementsDescriptors(parent)) {
      final String childElementName = childElementDescriptor.getName();
      int prevSubTagNum = subTagNum;
      while (subTagNum < subTags.length - 1 && subTags[subTagNum + 1].getName().equals(childElementName)) {
        subTagNum++;
      }
      if (childElementName.equals(child.getLocalName())) {
        // insert child just after anchor
        // insert into the position specified by index
        subTagNum = index == -1 || index > subTagNum - prevSubTagNum ? subTagNum : prevSubTagNum + index;
        return (XmlTag)(subTagNum == -1 ? parent.addBefore(child, subTags[0]) : parent.addAfter(child, subTags[subTagNum]));
      }
    }
    return (XmlTag)parent.add(child);
  }

  public static String getAttributeValue(XmlTag tag, String name) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (name.equals(attribute.getName())) return attribute.getValue();
    }
    return null;
  }

  public static XmlTag findOnAnyLevel(XmlTag root, String[] chain) {
    XmlTag curTag = root;
    for (String s : chain) {
      curTag = curTag.findFirstSubTag(s);
      if (curTag == null) return null;
    }

    return curTag;
  }

  public static XmlTag findSubTag(XmlTag rootTag, String path) {
    String[] pathElements = path.split("/");

    XmlTag curTag = rootTag;
    for (String curTagName : pathElements) {
      curTag = curTag.findFirstSubTag(curTagName);
      if (curTag == null) break;
    }
    return curTag;
  }

  public static XmlTag findSubTagWithValue(XmlTag rootTag, String tagName, String tagValue) {
    if (rootTag == null) return null;
    final XmlTag[] subTags = rootTag.findSubTags(tagName);
    for (XmlTag subTag : subTags) {
      if (subTag.getValue().getTrimmedText().equals(tagValue)) {
        return subTag;
      }
    }
    return null;
  }

  // Read the function name and parameter names to find out what this function does... :-)
  public static XmlTag find(String subTag, String withValue, String forTag, XmlTag insideRoot) {
    final XmlTag[] forTags = insideRoot.findSubTags(forTag);

    for (XmlTag tag : forTags) {
      final XmlTag[] allTags = tag.findSubTags(subTag);

      for (XmlTag curTag : allTags) {
        if (curTag.getName().equals(subTag) && curTag.getValue().getTrimmedText().equalsIgnoreCase(withValue)) {
          return tag;
        }
      }
    }

    return null;
  }

  public static boolean isInAntBuildFile(XmlFile file) {
    if (file == null) return false;
    if (file.getCopyableUserData(XmlFile.ANT_BUILD_FILE) != null) {
      return true;
    }
    XmlDocument document = file.getDocument();
    if (document != null) {
      XmlTag rootTag = document.getRootTag();
      if (rootTag != null) {
        return ANT_URI.equals(rootTag.getNamespace());
      }
    }
    return false;
  }

  @NonNls
  public static String[][] getDefaultNamespaces(final XmlDocument document) {
    final XmlFile file = getContainingFile(document);

    final XmlTag tag = document.getRootTag();
    if (tag == null) return new String[][]{new String[]{"", EMPTY_URI}};

    if (file != null) {
      final @NotNull XmlFileNSInfoProvider[] nsProviders = ApplicationManager.getApplication().getComponents(XmlFileNSInfoProvider.class);

      NextProvider:
      for (XmlFileNSInfoProvider nsProvider : nsProviders) {
        final String[][] pairs = nsProvider.getDefaultNamespaces(file);
        if (pairs != null && pairs.length > 0) {

          for (final String[] nsMapping : pairs) {
            if (nsMapping == null || nsMapping.length != 2 || nsMapping[0] == null || nsMapping[1] == null) {
              LOG.debug("NSInfoProvider " + nsProvider + " gave wrong info about " + file.getVirtualFile());
              continue NextProvider;
            }
          }
          return pairs;
        }
      }
    }

    String namespace = getDtdUri(document);
    if (namespace != null) return new String[][]{new String[]{"", namespace}};

    if ("taglib".equals(tag.getName())) {
      return new String[][]{new String[]{"", TAGLIB_1_2_URI}};
    }

    if (file != null) {
      final FileType fileType = file.getFileType();

      if (fileType == StdFileTypes.HTML || fileType == StdFileTypes.XHTML) {
        return new String[][]{new String[]{"", XHTML_URI}};
      }
      else if (fileType == StdFileTypes.JSPX || fileType == StdFileTypes.JSP) {
        String baseLanguageNameSpace = EMPTY_URI;
        if (PsiUtil.isInJspFile(file)) {
          final Language baseLanguage = PsiUtil.getJspFile(file).getViewProvider().getTemplateDataLanguage();
          if (baseLanguage == StdLanguages.HTML || baseLanguage == StdLanguages.XHTML) {
            baseLanguageNameSpace = XHTML_URI;
          }
        }

        return new String[][]{new String[]{"", baseLanguageNameSpace}, new String[]{"jsp", JSP_URI}};
      }
    }

    return new String[][]{new String[]{"", EMPTY_URI}};
  }


  public static String getDtdUri(XmlDocument document) {
    if (document.getProlog() != null) {
      final XmlDoctype doctype = document.getProlog().getDoctype();
      if (doctype != null) {
        return doctype.getDtdUri();
      }
    }
    return null;
  }

  private static void computeTag(XmlTag tag,
                                 final Map<String, List<String>> tagsMap,
                                 final Map<String, List<MyAttributeInfo>> attributesMap) {
    if (tag == null) {
      return;
    }
    final String tagName = tag.getName();

    List<MyAttributeInfo> list = attributesMap.get(tagName);
    if (list == null) {
      list = new ArrayList<MyAttributeInfo>();
      final XmlAttribute[] attributes = tag.getAttributes();
      for (final XmlAttribute attribute : attributes) {
        list.add(new MyAttributeInfo(attribute.getName()));
      }
    }
    else {
      final XmlAttribute[] attributes = tag.getAttributes();
      Collections.sort(list);
      Arrays.sort(attributes, new Comparator<XmlAttribute>() {
        public int compare(XmlAttribute attr1, XmlAttribute attr2) {
          return attr1.getName().compareTo(attr2.getName());
        }
      });

      final Iterator<MyAttributeInfo> iter = list.iterator();
      list = new ArrayList<MyAttributeInfo>();
      int index = 0;
      while (iter.hasNext()) {
        final MyAttributeInfo info = iter.next();
        boolean requiredFlag = false;
        while (attributes.length > index) {
          if (info.compareTo(attributes[index]) != 0) {
            if (info.compareTo(attributes[index]) < 0) {
              break;
            }
            if (attributes[index].getValue() != null) list.add(new MyAttributeInfo(attributes[index].getName(), false));
            index++;
          }
          else {
            requiredFlag = true;
            index++;
            break;
          }
        }
        info.myRequired &= requiredFlag;
        list.add(info);
      }
      while (attributes.length > index) {
        if (attributes[index].getValue() != null) {
          list.add(new MyAttributeInfo(attributes[index++].getName(), false));
        }
        else {
          index++;
        }
      }
    }
    attributesMap.put(tagName, list);
    final List<String> tags = tagsMap.get(tagName) != null ? tagsMap.get(tagName) : new ArrayList<String>();
    tagsMap.put(tagName, tags);
    tag.processElements(new FilterElementProcessor(new ClassFilter(XmlTag.class)) {
      public void add(PsiElement element) {
        XmlTag tag = (XmlTag)element;
        if (!tags.contains(tag.getName())) {
          tags.add(tag.getName());
        }
        computeTag(tag, tagsMap, attributesMap);
      }
    }, tag);
  }

  public static XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag) {
    return findXmlDescriptorByType(xmlTag, null);
  }

  @Nullable
  public static XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag, @Nullable XmlTag context) {
    XmlElementDescriptor elementDescriptor = null;
    String type = xmlTag.getAttributeValue("type", XML_SCHEMA_INSTANCE_URI);

    if (type == null) {
      String ns = xmlTag.getNamespace();
      if (XmlUtil.ourSchemaUrisList.indexOf(ns) >= 0) {
        type = xmlTag.getAttributeValue("type", null);
      }
    }

    if (type != null) {
      final String namespaceByPrefix = findNamespaceByPrefix(findPrefixByQualifiedName(type), xmlTag);
      XmlNSDescriptor typeDecr = xmlTag.getNSDescriptor(namespaceByPrefix, true);
      if (typeDecr == null && namespaceByPrefix.length() == 0 && context != null) {
        typeDecr = context.getNSDescriptor("", true);
      }

      if (typeDecr instanceof XmlNSDescriptorImpl) {
        final XmlNSDescriptorImpl schemaDescriptor = (XmlNSDescriptorImpl)typeDecr;
        elementDescriptor = schemaDescriptor.getDescriptorByType(type, xmlTag);
      }
    }

    return elementDescriptor;
  }

  public static boolean collectEnumerationValues(final XmlTag element, final HashSet<String> variants) {
    boolean exaustiveEnum = true;

    for (final XmlTag tag : element.getSubTags()) {
      @NonNls final String localName = tag.getLocalName();

      if (localName.equals("enumeration")) {
        final String attributeValue = tag.getAttributeValue("value");
        if (attributeValue != null) variants.add(attributeValue);
      }
      else if (localName.equals("union")) {
        exaustiveEnum = false;
        collectEnumerationValues(tag, variants);
      }
      else if (!localName.equals("annotation")) {
        // don't go into annotation
        exaustiveEnum &= collectEnumerationValues(tag, variants);
      }
    }
    return exaustiveEnum;
  }

  public static XmlTag createChildTag(final XmlTag xmlTag,
                                      String localName,
                                      String namespace,
                                      String bodyText,
                                      boolean enforceNamespacesDeep) {
    String qname;
    final String prefix = xmlTag.getPrefixByNamespace(namespace);
    if (prefix != null && prefix.length() > 0) {
      qname = prefix + ":" + localName;
    }
    else {
      qname = localName;
    }
    try {
      @NonNls StringBuilder tagStartBuilder = StringBuilderSpinAllocator.alloc();
      String tagStart;
      try {
        tagStartBuilder.append(qname);
        if (xmlTag.getPrefixByNamespace(namespace) == null &&
            !(StringUtil.isEmpty(xmlTag.getNamespacePrefix()) && namespace.equals(xmlTag.getNamespace()))) {
          tagStartBuilder.append(" xmlns=\"");
          tagStartBuilder.append(namespace);
          tagStartBuilder.append("\"");
        }
        tagStart = tagStartBuilder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(tagStartBuilder);
      }
      XmlTag retTag;
      if (bodyText != null && bodyText.length() > 0) {
        retTag = xmlTag.getManager().getElementFactory().createTagFromText("<" + tagStart + ">" + bodyText + "</" + qname + ">");
        if (enforceNamespacesDeep) {
          retTag.acceptChildren(new PsiRecursiveElementVisitor() {
            public void visitXmlTag(XmlTag tag) {
              final String namespacePrefix = tag.getNamespacePrefix();
              if (namespacePrefix.length() == 0) {
                String qname;
                if (prefix != null && prefix.length() > 0) {
                  qname = prefix + ":" + tag.getLocalName();
                }
                else {
                  qname = tag.getLocalName();
                }
                try {
                  tag.setName(qname);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
              super.visitXmlTag(tag);
            }
          });
        }
      }
      else {
        retTag = xmlTag.getManager().getElementFactory().createTagFromText("<" + tagStart + "/>");
      }
      return retTag;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  public static Pair<XmlTagChild, XmlTagChild> findTagChildrenInRange(final PsiFile file, int startOffset, int endOffset) {
    PsiElement elementAtStart = file.findElementAt(startOffset);
    PsiElement elementAtEnd = file.findElementAt(endOffset - 1);
    if (elementAtStart instanceof PsiWhiteSpace) {
      startOffset = elementAtStart.getTextRange().getEndOffset();
      elementAtStart = file.findElementAt(startOffset);
    }
    if (elementAtEnd instanceof PsiWhiteSpace) {
      endOffset = elementAtEnd.getTextRange().getStartOffset();
      elementAtEnd = file.findElementAt(endOffset - 1);
    }
    if (elementAtStart == null || elementAtEnd == null) return null;

    XmlTagChild first = PsiTreeUtil.getParentOfType(elementAtStart, XmlTagChild.class);
    if (first == null) return null;

    if (first.getTextRange().getStartOffset() != startOffset) {
      //Probably 'first' starts with whitespace
      PsiElement elementAt = file.findElementAt(first.getTextRange().getStartOffset());
      if (!(elementAt instanceof PsiWhiteSpace) || elementAt.getTextRange().getEndOffset() != startOffset) return null;
    }

    XmlTagChild last = first;
    while (last != null && last.getTextRange().getEndOffset() < endOffset) {
      last = PsiTreeUtil.getNextSiblingOfType(last, XmlTagChild.class);
    }

    if (last == null) return null;
    if (last.getTextRange().getEndOffset() != elementAtEnd.getTextRange().getEndOffset()) {
      //Probably 'last' ends with whitespace
      PsiElement elementAt = file.findElementAt(last.getTextRange().getEndOffset() - 1);
      if (!(elementAt instanceof PsiWhiteSpace) || elementAt.getTextRange().getStartOffset() != endOffset) {
        return null;
      }
    }

    return new Pair<XmlTagChild, XmlTagChild>(first, last);
  }

  public static boolean isSimpleXmlAttributeValue(final String unquotedValue, final XmlAttributeValue context) {
    for (int i = 0; i < unquotedValue.length(); ++i) {
      final char ch = unquotedValue.charAt(i);
      if (!Character.isJavaIdentifierPart(ch) && ch != ':' && ch != '-') {
        final XmlFile file = PsiTreeUtil.getParentOfType(context, XmlFile.class);
        if (file != null) return !tagFromTemplateFramework(file.getDocument().getRootTag());
        return false;
      }
    }
    return true;
  }

  public static boolean toCode(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (toCode(str.charAt(i))) return true;
    }
    return false;
  }

  public static boolean toCode(char ch) {
    return "<&>\u00a0".indexOf(ch) >= 0;
  }

  @Nullable
  public static PsiNamedElement findRealNamedElement(final @NotNull PsiNamedElement _element) {
    PsiElement currentElement = _element;
    final XmlEntityRef lastEntityRef = PsiTreeUtil.getParentOfType(currentElement, XmlEntityRef.class);

    while (!(currentElement instanceof XmlFile)) {
      PsiElement dependingElement = currentElement.getUserData(XmlElement.DEPENDING_ELEMENT);
      if (dependingElement == null) dependingElement = currentElement.getContext();
      currentElement = dependingElement;
      if (dependingElement == null) break;
    }

    if (currentElement instanceof XmlFile) {
      final String name = _element.getName();
      if (_element instanceof XmlEntityDecl) {
        final XmlEntityDecl cachedEntity = XmlEntityRefImpl.getCachedEntity((PsiFile)currentElement, name);
        if (cachedEntity != null) return cachedEntity;
      }

      final PsiNamedElement[] result = new PsiNamedElement[1];

      processXmlElements((XmlFile)currentElement, new PsiElementProcessor() {
        public boolean execute(final PsiElement element) {
          if (element instanceof PsiNamedElement) {
            final String elementName = ((PsiNamedElement)element).getName();

            if (elementName.equals(name) && _element.getClass().isInstance(element)) {
              result[0] = (PsiNamedElement)element;
              return false;
            }
            else if (lastEntityRef != null &&
                     element instanceof XmlEntityDecl &&
                     elementName.equals(lastEntityRef.getText().substring(1, lastEntityRef.getTextLength() - 1))) {
              result[0] = (PsiNamedElement)element;
              return false;
            }
          }

          return true;
        }
      }, true);

      return result[0];
    }

    return null;
  }

  private static class MyAttributeInfo implements Comparable {
    boolean myRequired = true;
    String myName = null;

    MyAttributeInfo(String name) {
      myName = name;
    }

    MyAttributeInfo(String name, boolean flag) {
      myName = name;
      myRequired = flag;
    }

    public int compareTo(Object o) {
      if (o instanceof MyAttributeInfo) {
        return myName.compareTo(((MyAttributeInfo)o).myName);
      }
      else if (o instanceof XmlAttribute) {
        return myName.compareTo(((XmlAttribute)o).getName());
      }
      return -1;
    }
  }

  public static String generateDocumentDTD(XmlDocument doc) {
    final Map<String, List<String>> tags = new LinkedHashMap<String, List<String>>();
    final Map<String, List<MyAttributeInfo>> attributes = new LinkedHashMap<String, List<MyAttributeInfo>>();
    final XmlTag rootTag = doc.getRootTag();
    computeTag(rootTag, tags, attributes);

    // For supporting not welformed XML
    for (PsiElement element = rootTag != null ? rootTag.getNextSibling() : null; element != null; element = element.getNextSibling()) {
      if (element instanceof XmlTag) {
        computeTag((XmlTag)element, tags, attributes);
      }
    }

    final StringBuilder buffer = new StringBuilder();
    for (final String tagName : tags.keySet()) {
      buffer.append(generateElementDTD(tagName, tags.get(tagName), attributes.get(tagName)));
    }
    return buffer.toString();
  }

  public static String generateElementDTD(String name, List<String> tags, List<MyAttributeInfo> attributes) {
    if (name == null || "".equals(name)) return "";
    if (name.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) return "";

    @NonNls final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      buffer.append("<!ELEMENT ").append(name).append(" ");
      if (tags.isEmpty()) {
        buffer.append("(#PCDATA)>\n");
      }
      else {
        buffer.append("(");
        final Iterator<String> iter = tags.iterator();
        while (iter.hasNext()) {
          final String tagName = iter.next();
          buffer.append(tagName);
          if (iter.hasNext()) {
            buffer.append("|");
          }
          else {
            buffer.append(")*");
          }
        }
        buffer.append(">\n");
      }
      if (!attributes.isEmpty()) {
        buffer.append("<!ATTLIST ").append(name);
        for (final MyAttributeInfo info : attributes) {
          buffer.append("\n    ").append(generateAttributeDTD(info));
        }
        buffer.append(">\n");
      }
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private static String generateAttributeDTD(MyAttributeInfo info) {
    if (info.myName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) return "";
    @NonNls final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      buffer.append(info.myName).append(" ");
      //if ("id".equals(info.myName)) {
      //  buffer.append("ID");
      //}
      //else if ("ref".equals(info.myName)) {
      //  buffer.append("IDREF");
      //} else {
      buffer.append("CDATA");
      //}
      if (info.myRequired) {
        buffer.append(" #REQUIRED");
      }
      else {
        buffer.append(" #IMPLIED");
      }
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  public static String trimLeadingSpacesInMultilineTagValue(@NonNls String tagValue) {
    return tagValue == null ? null : tagValue.replaceAll("\n\\s*", "\n");
  }

  public static String findNamespaceByPrefix(final String prefix, XmlTag contextTag) {
    return contextTag.getNamespaceByPrefix(prefix);
  }

  public static String findPrefixByQualifiedName(String name) {
    final int prefixEnd = name.indexOf(':');
    if (prefixEnd > 0) {
      return name.substring(0, prefixEnd);
    }
    return "";
  }

  public static String findLocalNameByQualifiedName(String name) {
    return name.substring(name.indexOf(':') + 1);
  }


  public static XmlFile getContainingFile(PsiElement element) {
    while (!(element instanceof XmlFile) && element != null) {
      final PsiElement context = element.getContext();
      if (context == null && PsiUtil.isInJspFile(element)) {
        element = PsiUtil.getJspFile(element);
      }
      else {
        element = context;
      }
    }
    return (XmlFile)element;
  }

  @Nullable
  public static String getSubTagValue(XmlTag tag, final String subTagName) {
    final XmlTag subTag = tag.findFirstSubTag(subTagName);
    if (subTag != null) {
      return subTag.getValue().getTrimmedText();
    }
    return null;
  }

  public static int getStartOffsetInFile(XmlTag xmlTag) {
    int off = 0;
    while (true) {
      off += xmlTag.getStartOffsetInParent();
      final PsiElement parent = xmlTag.getParent();
      if (!(parent instanceof XmlTag)) break;
      xmlTag = (XmlTag)parent;
    }
    return off;
  }

  public static XmlElement setNewValue(XmlElement tag, String value) throws IncorrectOperationException {
    if (tag instanceof XmlTag) {
      ((XmlTag)tag).getValue().setText(value);
      return tag;
    }
    else if (tag instanceof XmlAttribute) {
      XmlAttribute attr = (XmlAttribute)tag;
      attr.setValue(value);
      return attr;
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  public static String decode(@NonNls String text) {
    if (text.length() == 0) return text;
    if (text.charAt(0) != '&' || text.length() < 3) {
      if (text.indexOf('<') < 0 && text.indexOf('>') < 0) return text;
      return text.replaceAll("<!\\[CDATA\\[", "").replaceAll("\\]\\]>", "");
    }

    if (text.equals("&lt;")) {
      return "<";
    }
    if (text.equals("&gt;")) {
      return ">";
    }
    if (text.equals("&nbsp;")) {
      return "\u00a0";
    }
    if (text.equals("&amp;")) {
      return "&";
    }
    if (text.equals("&apos;")) {
      return "'";
    }
    if (text.equals("&quot;")) {
      return "\"";
    }
    if (text.startsWith("&quot;") && text.endsWith("&quot;")) {
      return "\"" + text.substring(6, text.length() - 6) + "\"";
    }
    if (text.startsWith("&#")) {
      text = text.substring(3, text.length() - 1);
      try {
        return String.valueOf((char)Integer.parseInt(text));
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }

    return text;
  }

  public static String unescape(String text) {
    return StringUtil.unescapeXml(text);
  }

  public static String escape(String text) {
    return StringUtil.escapeXml(text);
  }

  @NonNls private static final byte[] XML_PROLOG_START_BYTES = CharsetToolkit.getUtf8Bytes("<?xml");
  @NonNls private static final byte[] ENCODING_BYTES = CharsetToolkit.getUtf8Bytes("encoding");
  @NonNls private static final byte[] XML_PROLOG_END_BYTES = CharsetToolkit.getUtf8Bytes("?>");

  @Nullable
  public static String extractXmlEncodingFromProlog(VirtualFile file) {
    try {
      byte[] bytes = file.contentsToByteArray();
      return detect(bytes);
    }
    catch (IOException e) {
    }
    return null;
  }

  private static String detect(final byte[] bytes) {
    int start = 0;
    if (CharsetToolkit.hasUTF8Bom(bytes)) {
      start = CharsetToolkit.UTF8_BOM.length;
    }

    start = skipWhiteSpace(start, bytes);
    if (!ArrayUtil.startsWith(bytes, start, XML_PROLOG_START_BYTES)) return null;
    start += XML_PROLOG_START_BYTES.length;
    while (start < bytes.length) {
      start = skipWhiteSpace(start, bytes);
      if (ArrayUtil.startsWith(bytes, start, XML_PROLOG_END_BYTES)) return null;
      if (ArrayUtil.startsWith(bytes, start, ENCODING_BYTES)) {
        start += ENCODING_BYTES.length;
        start = skipWhiteSpace(start, bytes);
        if (start >= bytes.length || bytes[start] != '=') continue;
        start++;
        start = skipWhiteSpace(start, bytes);
        if (start >= bytes.length || bytes[start] != '\'' && bytes[start] != '\"') continue;
        byte quote = bytes[start];
        start++;
        StringBuilder encoding = new StringBuilder();
        while (start < bytes.length) {
          if (bytes[start] == quote) return encoding.toString();
          encoding.append((char)bytes[start++]);
        }
      }
      start++;
    }
    return null;
  }

  private static int skipWhiteSpace(int start, final byte[] bytes) {
    while (start < bytes.length) {
      char c = (char)bytes[start];
      if (!Character.isWhitespace(c)) break;
      start++;
    }
    return start;
  }

  @Nullable
  public static String extractXmlEncodingFromProlog(String text) {
    return detect(CharsetToolkit.getUtf8Bytes(text));
  }

  public interface DuplicationInfoProvider<T extends PsiElement> {
    @Nullable
    String getName(T t);

    @NotNull
    String getNameKey(T t, String name);

    @NotNull
    PsiElement getNodeForMessage(T t);
  }
}
