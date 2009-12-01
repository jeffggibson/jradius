/**
 * RadiusDictionary (for FreeRADIUS)    
 * Copyright (C) 2004-2006 PicoPoint, B.V.
 * Copyright (c) 2006-2007 David Bird <david@coova.com>
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package net.jradius.freeradius;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import net.jradius.log.RadiusLog;

/**
 * JRadius Dictionary builder for FreeRADIUS
 * <p>
 * See the comments for the main method for how to build a dictionary
 * library.
 *
 * @author David Bird
 */
@SuppressWarnings("unchecked")
public class RadiusDictionary
{
    private static final String ppkg = "net.jradius.packet.attribute";
    private boolean haveSeenJRadius = false;
    private final String bpkg;
    private final String sdir;
    private final String ddir;

    private static String defaultJRadiusDictionary = // trying to make it look nice p;)
        "VENDOR\t"    + "JRadius\t"              + "19211\n" +
        "ATTRIBUTE\t" + "JRadius-Request-Id\t"   + "1\t" + "string\t" + "JRadius\n" + 
        "ATTRIBUTE\t" + "JRadius-Session-Id\t"   + "2\t" + "string\t" + "JRadius\n" + 
        "ATTRIBUTE\t" + "JRadius-Proxy-Client\t" + "3\t" + "octets\t" + "JRadius\n";
    
    private static String fileHeader = 
        "// DO NOT EDIT THIS FILE DIRECTLY! - AUTOMATICALLY GENERATED\n" +
        "// Generated by: " + RadiusDictionary.class.toString() + "\n" +
        "// Generated on: " + new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(Calendar.getInstance().getTime()) + "\n";
    
    private LinkedHashMap attrMap = new LinkedHashMap();
    private LinkedHashMap vendorMap = new LinkedHashMap();
    private LinkedHashMap seenNames = new LinkedHashMap();
    private LinkedHashMap classNames = new LinkedHashMap();
    private String cVendor = null;
    private String cTLV = null;
    
    class AttrDesc 
    {
        public String name;
        public String num;
        public String type;
        public String extra;
        public String vendor;
        public LinkedHashMap values = null;
        public LinkedHashMap subAttributes = null;
        public AttrDesc(String n, String m, String t, String e, String v) 
        { 
            name = n; num = m; type = t; extra = e; vendor = v;
        }
    }

    class AttrValueDesc 
    {
        public LinkedList names = new LinkedList();
        public String num;
        public AttrValueDesc(String n, String m) 
        { 
            num = m;
            names.add(n);  
        }
        public void addName(String n)
        {
            for (Iterator i=names.iterator(); i.hasNext(); )
                if (i.next().equals(n))
                    return;
            names.add(n);  
        }
    }

    class VendorDesc
    {
        public String name;
        public String num;
        public String pkg;
        public String extra;
		public LinkedHashMap attrMap = new LinkedHashMap();
        public VendorDesc(String n, String m, String p, String e)
        {
            name = n; num = m; pkg = p; extra = e;
        }
        public String getFormat() 
        {
        	if (extra != null && extra.startsWith("format="))
        	{
        		return extra.substring(7);
        	}
        	return null; /* not custom */
        }
    }
    
    public RadiusDictionary(Reader in, String pkgName, String dictDir, String srcDir) throws IOException
    {
        bpkg = pkgName;
        ddir = dictDir;
        sdir = srcDir;
        readFile(new BufferedReader(in));
        if (!haveSeenJRadius)
        {
            try
            {
                readFile(new BufferedReader(new FileReader(dictDir + "/" + "dictionary.jradius")));
            }
            catch(Exception e)
            {
                RadiusLog.error("WARNING!! You have not included the JRadius Dictionary (dictionary.jradius)");
            }
        }
    }

    public RadiusDictionary(String fileName, String pkgName, String dictDir, String srcDir) throws IOException
    {
        this(new FileReader(dictDir + "/" + fileName), pkgName, dictDir, srcDir);
    }

    public void readFile(BufferedReader in) throws IOException
    {
        String line;
        while ((line = in.readLine()) != null)        
        {
            line = line.trim();
            String upperLine = line.toUpperCase();
            if (upperLine.startsWith("#")) continue;
            if (upperLine.startsWith("$INCLUDE"))
            {
                String parts[] = line.split("[\t ]+");
                String file = parts[1];
                if ("dictionary.jradius".equals(file)) haveSeenJRadius = true;
                RadiusLog.error("Including file: " + file);
                readFile(new BufferedReader(new FileReader(ddir + "/" + file)));
            }
            else if (upperLine.startsWith("BEGIN-TLV"))
            {
                String parts[] = line.split("[\t ]+");
                cTLV = parts[1];
            }
            else if (upperLine.startsWith("END-TLV"))
            {
            	cTLV = null;
            }
            else if (upperLine.startsWith("BEGIN-VENDOR"))
            {
                String parts[] = line.split("[\t ]+");
                cVendor = parts[1];
            }
            else if (upperLine.startsWith("END-VENDOR"))
            {
                cVendor = null;
            }
            else if (upperLine.startsWith("ATTRIBUTE"))
            {
                String parts[] = line.split("[\t ]+");
                String attrName = null;
                String attrNum = null;
                String attrType = null;
                String attrExtra = null;
                String attrVendor = null;
                VendorDesc vdesc = null;
                
                for (int i = 1; i < parts.length; i++)
                {
                    String p = parts[i].trim();
                    if (p.length() == 0) continue;
                    if (attrName == null) attrName = p;
                    else if (attrNum == null) attrNum = p;
                    else if (attrType == null) attrType = p;
                    else 
                    {
                        if ((vdesc = (VendorDesc)vendorMap.get(p)) != null)
                            attrVendor = p;
                        else
                            attrExtra = p;
                    }
                }
                
                if (attrName != null 
                		&& attrNum != null 
                		&& attrType != null 
                		&& !seenNames.containsKey(attrName.toLowerCase()))
                {
                    Map map = attrMap;
                    if (attrVendor == null && cVendor != null) 
                    {	
                        attrVendor = cVendor;
                        vdesc = (VendorDesc)vendorMap.get(cVendor);
                    }
                    if (vdesc != null)
                    {
                    	if (cTLV != null)
                    	{
                    		AttrDesc attrDesc = (AttrDesc)vdesc.attrMap.get(cTLV);
                    		if (attrDesc.subAttributes == null) attrDesc.subAttributes = new LinkedHashMap();
                    		map = attrDesc.subAttributes;
                    	}
                    	else
                    	{
                    		map = vdesc.attrMap;
                    	}
                    }
                    map.put(attrName, new AttrDesc(attrName, attrNum, attrType, attrExtra, attrVendor));
                    //RadiusLog.error(line);
                	System.out.println("Seen = " + attrName);
                    seenNames.put(attrName.toLowerCase(), attrNum);
                }
            }
            else if (upperLine.startsWith("VALUE"))
            {
                String parts[] = line.split("[\t ]+");
                String attrName = null;
                String attrValueName = null;
                String attrValueNum = null;
                for (int i = 1; i < parts.length; i++)
                {
                    String p = parts[i].trim();
                    if (p.length() == 0) continue;
                    if (attrName == null) attrName = p;
                    else if (attrValueName == null) attrValueName = p;
                    else if (attrValueNum == null) attrValueNum = p;
                }
                if (attrName != null && attrValueNum != null && attrValueName != null)
                {
                    AttrDesc desc = (AttrDesc)attrMap.get(attrName);
                    if (desc == null && cVendor != null)
                    {
                        VendorDesc vendorDesc = (VendorDesc)vendorMap.get(cVendor);
                        desc = (AttrDesc)vendorDesc.attrMap.get(attrName);
                    }
                    if (desc != null)
                    {
                        AttrValueDesc avd;
                        if (desc.values == null) desc.values = new LinkedHashMap();
                        if ((avd = (AttrValueDesc)desc.values.get(attrValueNum)) == null)
                            desc.values.put(attrValueNum, new AttrValueDesc(attrValueName, attrValueNum));
                        else 
                            avd.addName(attrValueName);
                    }
                }
            }
            else if (upperLine.startsWith("VENDOR"))
            {
                String parts[] = line.split("[\t ]+");
                String vendorName = null;
                String vendorNum = null;
                String vendorExtra = null;
                String vendorPkg = null;
                for (int i = 1; i < parts.length; i++)
                {
                    String p = parts[i].trim();
                    if (p.length() == 0) continue;
                    if (vendorName == null) vendorName = p;
                    else if (vendorNum == null) vendorNum = p;
                    else if (vendorExtra == null) vendorExtra = p;
                }
                if (vendorName != null && vendorNum != null)
                {
                    String vendor = "vsa_" + vendorName.toLowerCase().replaceAll("-",".");
                    vendorPkg = bpkg + "." + vendor;

                    if (vendorExtra != null)
                    {
                    	
                    }

                    vendorMap.put(vendorName, new VendorDesc(vendorName, vendorNum, vendorPkg, vendorExtra));
                    //RadiusLog.error(line);
                }
            }
        }
        return;
    }
    
    public void writeAttrMap(Map map, String pkg, String vName, String cName, String pName, boolean withVendors)
    {
        String dir = sdir + "/" + pkg.replaceAll("\\.","/");
        Iterator iter = map.values().iterator();
        String dictFile = dir + "/" + cName + ".java";
        PrintWriter dict = null;
        
        StringBuffer loadAttributes = new StringBuffer();
        StringBuffer loadAttributesNames = new StringBuffer();

        (new File(dir)).mkdirs();

        if (cName != null)
        {
	        try
	        {
	            dict = new PrintWriter(new FileWriter(dictFile));
	            dict.println(fileHeader);
	            dict.println("package " + pkg + ";");
	            dict.println("");
	            dict.println("import java.util.Map;");
	            dict.println("");
	            if (withVendors)
	            {
	                dict.println("import net.jradius.packet.attribute.AttributeDictionary;");
	            }
	            else
	            {
	                dict.println("import net.jradius.packet.attribute.VSADictionary;");
	            }
	            dict.println("");
	            dict.println("/**");
	            dict.println(" * Dictionary for package " + pkg);
	            dict.println(" * @author " + RadiusDictionary.class.toString());
	            dict.println(" */");
	            dict.print("public class " + cName);
	            if (withVendors)
	            {
	                dict.print(" implements AttributeDictionary");
	            }
	            else
	            {
	                dict.print(" implements VSADictionary");
	            }
	            dict.println("\n{");
	            if (withVendors)
	            {
	                dict.println("    public void loadVendorCodes(Map<Long, Class<?>> map)");
	                dict.println("    {");
	                Iterator iter2 = vendorMap.values().iterator();
	                while (iter2.hasNext())
	                {
	                    VendorDesc vdesc = (VendorDesc)iter2.next();
	                    dict.println("        map.put(new Long(" + vdesc.num + "L), " + vdesc.pkg + ".VSADictionaryImpl.class);");
	                }
	                dict.println("    }");
	                dict.println("");
	            }
	            else
	            {
	                dict.println("    public String getVendorName() { return \"" + vName + "\"; }\n");
	            }
	        }
	        catch (Exception e)
	        {
	            e.printStackTrace();
	        }
	
	        loadAttributes.append("    public void loadAttributes(Map<Long, Class<?>> map)\n");
	        loadAttributes.append("    {\n");
	
	        loadAttributesNames.append("    public void loadAttributesNames(Map<String, Class<?>> map)\n");
	        loadAttributesNames.append("    {\n");
	    }

        while (iter.hasNext())
        {
            AttrDesc desc = (AttrDesc)iter.next();
            VendorDesc vdesc = null;
            
            StringBuffer fileSB = new StringBuffer(dir);
            String pkgPath = pkg;
                        
            if (withVendors && desc.vendor != null)
            {
                String vendor = "vsa_" + desc.vendor.toLowerCase().replaceAll("-",".");
                fileSB.append("/").append(vendor.replaceAll("\\.", "/"));
                pkgPath += "." + vendor;
            }

            if (desc.vendor != null)
            {
                vdesc = (VendorDesc)vendorMap.get(desc.vendor);
            }

            String implementsInterface = null;
            String className = "Attr_" + clean(desc.name);
            String parentName = "RadiusAttribute";
            if (!withVendors) parentName = "VSAttribute";
            
            String parentImport = parentName;
            
            if (desc.subAttributes != null)
            {
            	parentName = "VSAWithSubAttributes";
            	parentImport = "VSAWithSubAttributes";
            }
            
            if (pName != null)
            {
            	parentName = "SubAttribute";
            	parentImport = "SubAttribute";
            }

            String valueClass = "OctetsValue";
            String valueArgs = "";
            String extraImport = null;
            String extraUtils = null;
            int integerLength = 4;
            
            (new File(fileSB.toString())).mkdirs();
            
            fileSB.append("/").append(className).append(".java");
            
            String file = fileSB.toString();
 
            if (desc.type.startsWith("string"))
            {
                if (desc.extra != null && "encrypt=1".equals(desc.extra))
                {
                    valueClass = "EncryptedStringValue";
                }
                else
                {
                    valueClass = "StringValue";
                }
            }
            if (desc.type.startsWith("integer"))
            {
                valueClass = "IntegerValue";
            }
            if (desc.type.startsWith("singed"))
            {
                valueClass = "IntegerValue";
            }
            if (desc.type.startsWith("date"))
            {
                valueClass = "DateValue";
                extraUtils = "import java.util.Date;\n";
            }
            if (desc.type.startsWith("ipaddr"))
            {
                valueClass = "IPAddrValue";
                extraUtils = "import java.net.InetAddress;\n";
            }
            if (desc.type.startsWith("ipv6addr"))
            {
                valueClass = "IPv6AddrValue";
                extraUtils = "import java.net.InetAddress;\n";
            }
            if (desc.type.startsWith("byte"))
            {
                valueClass = "IntegerValue";
                integerLength = 1;
            }
            if (desc.type.startsWith("short"))
            {
                valueClass = "IntegerValue";
                integerLength = 2;
            }
            // WiMAX: signed
            if (desc.type.startsWith("signed"))
            {
                valueClass = "SignedValue";
            }
            // WiMAX: combo-ip
            if (desc.type.startsWith("combo-ip"))
            {
                valueClass = "ComboIPAddrValue";
            }
            if (desc.values != null)
            {
                valueClass = "NamedValue";
                valueArgs = "map != null ? map : (map = new NamedValueMap())";
            }

            /*
            if (withVendors && desc.vendor != null)
            {
                extraImport = valueClass;
                valueArgs = "new " + valueClass + "(" + valueArgs + ")";
                valueClass = "VSAValue";
            }
            */
            
            if (desc.subAttributes != null)
            {
            	valueClass = "TLVValue";
            	valueArgs = "VENDOR_ID, VSA_TYPE, getSubAttributes()";
            }
            
            try
            {
                PrintWriter writer = new PrintWriter(new FileWriter(file));
                writer.println(fileHeader);
                writer.println("package " + pkgPath + ";");
                writer.println("");
                writer.println("import java.io.Serializable;");
                if (desc.values != null)
                {
                    writer.println("import java.util.LinkedHashMap;");
                    writer.println("import java.util.Map;");
                    writer.println("");
                }
                if (extraUtils != null)
                {
                    writer.println(extraUtils);
                    writer.println("");
                }
                writer.println("import " + ppkg + "." + parentImport + ";");
                if (implementsInterface != null)
                {
                    writer.println("import " + ppkg + "." + implementsInterface + ";");
                }
                writer.println("import " + ppkg + ".value." + valueClass + ";");
                if (desc.values != null && integerLength < 4)
                    writer.println("import " + ppkg + ".value.IntegerValue;");
                if (extraImport != null)
                {
                    writer.println("import net.jradius.packet.attribute.value." + extraImport + ";");
                }
                writer.println("");
                writer.println("/**");
                writer.println(" * Attribute Name: " + desc.name + "<br>");
                if (withVendors)	
                {
                    writer.print(" * Attribute Type: " + desc.num);
                    if (parseInt(desc.num) > 255)
                    {
                        writer.print(" (FreeRADIUS Internal Attribute)<br>");
                    }
                    writer.println("<br>");
                }
                else
                {
                    writer.println(" * Attribute Type: 26<br>");
                    writer.println(" * Vendor Id: " + vdesc.num + "<br>");
                    writer.println(" * VSA Type: " + desc.num + "<br>");
                }
                writer.println(" * Value Type: " + valueClass + "<br>");
                if (desc.values != null)
                {
                    writer.println(" * Possible Values: <br>");
                    writer.println(" * <ul>");
                    Iterator iter2 = desc.values.values().iterator();
                    while (iter2.hasNext())
                    {
                        AttrValueDesc avdesc = (AttrValueDesc)iter2.next();
                        for (Iterator i = avdesc.names.iterator(); i.hasNext(); )
                            writer.println(" * <li> " + i.next() + " (" + avdesc.num + ")");
                    }
                    writer.println(" * </ul>");
                }
                
                classNames.put(className, desc.num);
                
                writer.println(" *");
                writer.println(" * @author " + RadiusDictionary.class.toString());
                writer.println(" */");
                writer.println("public final class " + className + " extends " + parentName + (implementsInterface != null ? " implements "+implementsInterface : ""));
                writer.println("{");
                writer.println("    public static final String NAME = \"" + desc.name + "\";");
                
                String attributeType = desc.num;

                if (pName != null)
                {
                    writer.println("    public static final int VENDOR_ID = " + ((VendorDesc)vendorMap.get(desc.vendor)).num + ";");
                    writer.println("    public static final int PARENT_TYPE = "+classNames.get(pName)+";");
                    writer.println("    public static final int VSA_TYPE = (" + desc.num + " << 8) | PARENT_TYPE;");
                    writer.println("    public static final long TYPE = ((VENDOR_ID & 0xFFFF) << 16) | VSA_TYPE;");
                }
                else if (withVendors)	
                {
                    writer.println("    public static final long TYPE = " + desc.num + ";");
                }
                else
                {
                    attributeType = "26";
                    writer.println("    public static final int VENDOR_ID = " + ((VendorDesc)vendorMap.get(desc.vendor)).num + ";");
                    writer.println("    public static final int VSA_TYPE = " + desc.num + ";");
                    writer.println("    public static final long TYPE = ((VENDOR_ID & 0xFFFF) << 16) | VSA_TYPE;");
                }
                
                writer.println("");
                writer.println("    public static final long serialVersionUID = TYPE;");
                writer.println("");
                
                if (desc.values != null)
                {
                    Iterator iter2 = desc.values.values().iterator();
                    Map names = new LinkedHashMap();
                    while (iter2.hasNext())
                    {
                        AttrValueDesc avdesc = (AttrValueDesc)iter2.next();
                        for (Iterator i = avdesc.names.iterator(); i.hasNext(); )
                        {
                            String name = clean((String)i.next());
                            if (names.get(name) == null)
                            {
                                names.put(name, name);
                                writer.println("    public static final Long " + name + " = new Long(" + avdesc.num + "L);");
                            }
                        }
                    }

                    writer.println("");
                    writer.println("    protected class NamedValueMap implements NamedValue.NamedValueMap");
                    writer.println("    {");

                    iter2 = desc.values.values().iterator();
                    String pvalues=" ";
                    while (iter2.hasNext())
                    {
                        AttrValueDesc avdesc = (AttrValueDesc)iter2.next();
                        pvalues+="new Long("+avdesc.num+"L),";
                    }
                    writer.println("        public Long[] knownValues = {"+pvalues.substring(0, pvalues.length()-1)+"};");
                    writer.println("");
                    writer.println("        public Long[] getKnownValues() { return knownValues; }");
                    writer.println("");
                    writer.println("        public Long getNamedValue(String name)");
                    writer.println("        {");
  
                    iter2 = desc.values.values().iterator();
                    while (iter2.hasNext())
                    {
                    	AttrValueDesc avdesc = (AttrValueDesc)iter2.next();
                        for (Iterator i = avdesc.names.iterator(); i.hasNext(); )
                        {
                            String name = (String)i.next();
                            writer.println("            if (\""+name+"\".equals(name)) return new Long("+avdesc.num+"L);");
                        }
                    }
                    writer.println("            return null;");
                    writer.println("        }");

                    writer.println("");
                    writer.println("        public String getNamedValue(Long value)");
                    writer.println("        {");
                    iter2 = desc.values.values().iterator();
                    while (iter2.hasNext())
                    {
                        AttrValueDesc avdesc = (AttrValueDesc)iter2.next();
                        Iterator i = avdesc.names.iterator(); 
                        if (i != null && i.hasNext())
                        {
                            // The last one defined is the one used for number to String lookups!
                            String name = (String)i.next();
                            writer.println("            if (new Long(" + avdesc.num + "L).equals(value)) return \""+name+"\";");
                        }
                    }
                    writer.println("            return null;");
                    writer.println("        }");
                    writer.println("    };");
                    writer.println("");
                    writer.println("    public static NamedValueMap map = null;");
                }

                writer.println("    public void setup()");
                writer.println("    {");
                writer.println("        attributeName = NAME;");
                writer.println("        attributeType = " + attributeType + ";");
                
                if (pName != null)
                {
                    writer.println("        setParentClass("+pName+".class);");
                }
                
                if (pName != null || !withVendors)
                {
                    writer.println("        vendorId = VENDOR_ID;");
                    writer.println("        vsaAttributeType = VSA_TYPE;");
                }
                
                if (vdesc != null && vdesc.getFormat() != null)
                {
                    writer.println("        setFormat(\""+vdesc.getFormat()+"\");");
                }
                
                writer.println("        attributeValue = new " + valueClass + "(" + valueArgs + ");");
                
                if (valueClass.equals("IntegerValue") && integerLength < 4)
                {
                    writer.println("        ((IntegerValue)attributeValue).setLength("+integerLength+");");
                }
                
                writer.println("    }");
                writer.println("");
                writer.println("    public " + className + "()");
                writer.println("    {");
                writer.println("        setup();");
                writer.println("    }");
                writer.println("");
                writer.println("    public " + className + "(Serializable o)");
                writer.println("    {");
                writer.println("        setup(o);");
                writer.println("    }");
                /*if (desc.values != null)
                {
                    writer.println("");
                    writer.println("    public static Map getValueMap()");
                    writer.println("    {");
                    writer.println("        return valueMap;");
                    writer.println("    }");
                }*/
                writer.println("}");
                writer.close();
                
                if (cName != null && !withVendors || desc.vendor == null)
                {
                    loadAttributes.append("        map.put(new Long(" + desc.num + "L), " + className + ".class);\n");
                    loadAttributesNames.append("        map.put(" + className + ".NAME, " + className + ".class);\n");
                    if (desc.subAttributes != null)
                    {
                    	for (Object obj : desc.subAttributes.values())
                    	{
                    		AttrDesc at = (AttrDesc) obj;
                    		String cn = "Attr_" + clean(at.name);
                    		long value = ((Integer.parseInt(at.num)) << 8) | (Integer.parseInt(desc.num) & 0xFF);
                            loadAttributes.append("        map.put(new Long(" + value + "L), " + cn + ".class);\n");
                            loadAttributesNames.append("        map.put(" + cn + ".NAME, " + cn + ".class);\n");
                    	}
                    }
                } 
            }
            catch (Exception e)
            {
                RadiusLog.error(e.getMessage(), e);
            }

            if (desc.subAttributes != null)
            {
            	writeAttrMap(desc.subAttributes, pkg, vName, null, className, false);
            }

            RadiusLog.info(desc.name);
        }

        loadAttributes.append("    }\n");
        loadAttributesNames.append("    }\n");
        
        if (dict != null)
        {
	        dict.println(loadAttributes.toString());
	        dict.print(loadAttributesNames.toString());
	        try
	        {
	            dict.println("}");
	            dict.close();
	        }
	        catch (Exception e)
	        {
	            e.printStackTrace();
	        }
        }
        
        if (withVendors)
        {
            Iterator iter2 = vendorMap.values().iterator();
            while (iter2.hasNext())
            {
                VendorDesc vdesc = (VendorDesc)iter2.next();
                writeAttrMap(vdesc.attrMap, vdesc.pkg, vdesc.name, "VSADictionaryImpl", null, false);
            }
        }
    }

    public int parseInt(String s)
    {
        if (s.startsWith("0x"))
            return Integer.parseInt(s.substring(2), 16);

        return Integer.parseInt(s);
    }
    
    public void writeJavaClasses()
    {
        writeAttrMap(attrMap, bpkg, null, "AttributeDictionaryImpl", null, true);
    }
    
    private String clean(String s)
    {
        s = s.replaceAll("-", "_");
        s = s.replaceAll("[^a-zA-Z0-9]+", "");
        if (Character.isDigit(s.charAt(0))) 
            s = "_" + s;
        return s;
    }

    /**
     * Main method of the dictionary builder. Requires 3 command line
     * arguments: package name, dictionary directory, and java source
     * directory. For example (on one line):
     * <blockquote>
     * java net.jradius.freeradius.RadiusDictionary net.jradius.dictionary /path-to-freeradius-dictionary /path-to-java-source-directory
     * </blockquote>
     * 
     * @param args
     */
    public static void main(String[] args)
    {
        if (args.length != 3)
        {
            System.err.println("Requires 3 arguments: [package-name] [dictionary-dir] [java-src-dir]");
            System.err.println("\tpackage-name:    Name of the Java package to be built (e.g. net.jradius.dictionary)");
            System.err.println("\tdictionary-dir:  Directory where the FreeRADIUS 'dictionary' file is");
            System.err.println("\tjava-src--dir:   Directory where to write Java classes");
            return;
        }
        String file = "dictionary";
        String pkg  = args[0];
        String dDir = args[1];
        String jDir = args[2];
        try
        {
            RadiusDictionary d = new RadiusDictionary(file, pkg, dDir, jDir);
            d.writeJavaClasses();
        }
        catch (Exception e)
        {
            RadiusLog.error(e.getMessage(), e);
        }
    }
}
