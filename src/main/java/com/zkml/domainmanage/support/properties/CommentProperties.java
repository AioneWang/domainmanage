package com.zkml.domainmanage.support.properties;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.Set;

/**
 * 带注释的Properties
 */
public class CommentProperties{

    private Properties properties = new Properties();

    private LinkedHashMap<String, String> commentMap = new LinkedHashMap<String,String>();

    private static final long serialVersionUID = 1L;

    public CommentProperties()  {
        super();
    }

    public synchronized void load(InputStream inStream) throws IOException {
        load(inStream,"utf-8");
    }

    public synchronized void load(InputStream inStream,String charset) throws IOException {
        load0(new LineReader(new InputStreamReader(inStream,charset)));
    }

    public synchronized void load(Reader reader) throws IOException {
        load0(new LineReader(reader));
    }

    private void load0 (LineReader lr) throws IOException {
        char[] convtBuf = new char[1024];
        int limit;
        int keyLen;
        int valueStart;
        char c;
        boolean hasSep;
        boolean precedingBackslash;

        boolean isComment = false;
        String comment = null;

        while ((limit = lr.readLine()) >= 0) {
            c = 0;
            keyLen = 0;
            valueStart = limit;
            hasSep = false;

            //System.out.println("line=<" + new String(lineBuf, 0, limit) + ">");
            precedingBackslash = false;
            while (keyLen < limit) {
                c = lr.lineBuf[keyLen];
                //need check if escaped.
                if ((c == '=' ||  c == ':') && !precedingBackslash) {
                    valueStart = keyLen + 1;
                    hasSep = true;
                    break;
                } else if ((c == ' ' || c == '\t' ||  c == '\f') && !precedingBackslash) {
                    valueStart = keyLen + 1;
                    break;
                } else if(c == '#'){
                    isComment = true;
                }
                if (c == '\\') {
                    precedingBackslash = !precedingBackslash;
                } else {
                    precedingBackslash = false;
                }
                keyLen++;
            }
            while (valueStart < limit) {
                c = lr.lineBuf[valueStart];
                if (c != ' ' && c != '\t' &&  c != '\f') {
                    if (!hasSep && (c == '=' ||  c == ':')) {
                        hasSep = true;
                    } else {
                        break;
                    }
                }
                valueStart++;
            }
            if(isComment){
                comment = loadConvert(lr.lineBuf, 0, limit, convtBuf);
                isComment = false;
                continue;
            }
            String key = loadConvert(lr.lineBuf, 0, keyLen, convtBuf);
            String value = loadConvert(lr.lineBuf, valueStart, limit - valueStart, convtBuf);
            properties.put(key, value);
            if(comment != null){
                commentMap.put(key,comment.replace("#",""));
                comment = null;
            }
        }
    }

    private String loadConvert (char[] in, int off, int len, char[] convtBuf) {
        if (convtBuf.length < len) {
            int newLen = len * 2;
            if (newLen < 0) {
                newLen = Integer.MAX_VALUE;
            }
            convtBuf = new char[newLen];
        }
        char aChar;
        char[] out = convtBuf;
        int outLen = 0;
        int end = off + len;

        while (off < end) {
            aChar = in[off++];
            if (aChar == '\\') {
                aChar = in[off++];
                if(aChar == 'u') {
                    // Read the xxxx
                    int value=0;
                    for (int i=0; i<4; i++) {
                        aChar = in[off++];
                        switch (aChar) {
                            case '0': case '1': case '2': case '3': case '4':
                            case '5': case '6': case '7': case '8': case '9':
                                value = (value << 4) + aChar - '0';
                                break;
                            case 'a': case 'b': case 'c':
                            case 'd': case 'e': case 'f':
                                value = (value << 4) + 10 + aChar - 'a';
                                break;
                            case 'A': case 'B': case 'C':
                            case 'D': case 'E': case 'F':
                                value = (value << 4) + 10 + aChar - 'A';
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "Malformed \\uxxxx encoding.");
                        }
                    }
                    out[outLen++] = (char)value;
                } else {
                    if (aChar == 't') aChar = '\t';
                    else if (aChar == 'r') aChar = '\r';
                    else if (aChar == 'n') aChar = '\n';
                    else if (aChar == 'f') aChar = '\f';
                    out[outLen++] = aChar;
                }
            } else {
                out[outLen++] = aChar;
            }
        }
        return new String (out, 0, outLen);
    }

    public boolean containsKey(String key){
        return properties.containsKey(key);
    }

    public String getProperty(String key){
        return properties.getProperty(key);
    }

    public String getProperty(String key,String defaultValue){
        return properties.getProperty(key,defaultValue);
    }

    public synchronized void remove(String key){
        this.commentMap.remove(key);
        this.properties.remove(key);
    }

    public String getComment(String key){
        return commentMap.get(key);
    }

    public synchronized void setProperty(String key , String value , String comment)  {
        this.commentMap.put(key, comment);
        this.setProperty(key, value);
    }

    public synchronized Object setProperty(String key , String value)  {
        return properties.setProperty(key, value);
    }

    public Set<String> stringPropertyNames(){
        return properties.stringPropertyNames();
    }

    public void store(OutputStream out,String comments,String charset) throws IOException {
        store(new BufferedWriter(new OutputStreamWriter(out, charset)), comments);
    }

    public void store(OutputStream out,String comments) throws IOException {
        store(out, comments,"utf-8");
    }

    public void store(Writer writer , String comments) throws IOException {
        BufferedWriter bufferedWriter = (writer instanceof BufferedWriter) ? (BufferedWriter)writer : new BufferedWriter(writer);
        if (comments != null)  {
            CommentProperties.writeComments(bufferedWriter, comments);
        }
        synchronized (this)   {
            Iterator<Object> iterator = properties.keySet().iterator();
            while(iterator.hasNext())  {
                String key = iterator.next().toString();
                String value = properties.getProperty(key);
                String comment = this.commentMap.get(key);
                key = saveConvert(key, true, false);
                value = saveConvert(value, false, false);
                if(!StringUtils.isBlank(comment))  {
                    writeComments(bufferedWriter, comment);
                }
                bufferedWriter.write(key+"="+value);
                bufferedWriter.newLine();
            }
        }
        bufferedWriter.flush();
    }

    private String saveConvert(String theString,boolean escapeSpace, boolean escapeUnicode) {
        int len = theString.length();
        int bufLen = len * 2;
        if (bufLen < 0) {
            bufLen = Integer.MAX_VALUE;
        }
        StringBuffer outBuffer = new StringBuffer(bufLen);
        for(int x=0; x<len; x++) {
            char aChar = theString.charAt(x);
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append('\\'); outBuffer.append('\\');
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch(aChar) {
                case ' ':
                    if (x == 0 || escapeSpace)
                        outBuffer.append('\\');
                    outBuffer.append(' ');
                    break;
                case '\t':outBuffer.append('\\'); outBuffer.append('t');
                    break;
                case '\n':outBuffer.append('\\'); outBuffer.append('n');
                    break;
                case '\r':outBuffer.append('\\'); outBuffer.append('r');
                    break;
                case '\f':outBuffer.append('\\'); outBuffer.append('f');
                    break;
                case '=': // Fall through
                case ':': // Fall through
                case '#': // Fall through
                case '!':
                    outBuffer.append('\\'); outBuffer.append(aChar);
                    break;
                default:
                    if (((aChar < 0x0020) || (aChar > 0x007e)) & escapeUnicode ) {
                        outBuffer.append('\\');
                        outBuffer.append('u');
                        outBuffer.append(toHex((aChar >> 12) & 0xF));
                        outBuffer.append(toHex((aChar >>  8) & 0xF));
                        outBuffer.append(toHex((aChar >>  4) & 0xF));
                        outBuffer.append(toHex( aChar        & 0xF));
                    } else {
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }

    private static void writeComments(BufferedWriter bw, String comments)throws IOException {
        bw.write("#");
        bw.write(comments);
        bw.newLine();

    }


    private static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }

    private static final char[] hexDigit = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};


    class LineReader {
        public LineReader(InputStream inStream) {
            this.inStream = inStream;
            inByteBuf = new byte[8192];
        }

        public LineReader(Reader reader) {
            this.reader = reader;
            inCharBuf = new char[8192];
        }

        byte[] inByteBuf;
        char[] inCharBuf;
        char[] lineBuf = new char[1024];
        int inLimit = 0;
        int inOff = 0;
        InputStream inStream;
        Reader reader;

        int readLine() throws IOException {
            int len = 0;
            char c = 0;

            boolean skipWhiteSpace = true;
            boolean isCommentLine = false;
            boolean isNewLine = true;
            boolean appendedLineBegin = false;
            boolean precedingBackslash = false;
            boolean skipLF = false;

            while (true) {
                if (inOff >= inLimit) {
                    inLimit = (inStream==null)?reader.read(inCharBuf)
                            :inStream.read(inByteBuf);
                    inOff = 0;
                    if (inLimit <= 0) {
                        if (len == 0 || isCommentLine) {
                            return -1;
                        }
                        if (precedingBackslash) {
                            len--;
                        }
                        return len;
                    }
                }
                if (inStream != null) {
                    //The line below is equivalent to calling a
                    //ISO8859-1 decoder.
                    c = (char) (0xff & inByteBuf[inOff++]);
                } else {
                    c = inCharBuf[inOff++];
                }
                if (skipLF) {
                    skipLF = false;
                    if (c == '\n') {
                        continue;
                    }
                }
                if (skipWhiteSpace) {
                    if (c == ' ' || c == '\t' || c == '\f') {
                        continue;
                    }
                    if (!appendedLineBegin && (c == '\r' || c == '\n')) {
                        continue;
                    }
                    skipWhiteSpace = false;
                    appendedLineBegin = false;
                }
                if (isNewLine) {
                    isNewLine = false;
                    /*if (c == '#' || c == '!') {
                        isCommentLine = true;
                        continue;
                    }*/
                }

                if (c != '\n' && c != '\r') {
                    lineBuf[len++] = c;
                    if (len == lineBuf.length) {
                        int newLength = lineBuf.length * 2;
                        if (newLength < 0) {
                            newLength = Integer.MAX_VALUE;
                        }
                        char[] buf = new char[newLength];
                        System.arraycopy(lineBuf, 0, buf, 0, lineBuf.length);
                        lineBuf = buf;
                    }
                    //flip the preceding backslash flag
                    if (c == '\\') {
                        precedingBackslash = !precedingBackslash;
                    } else {
                        precedingBackslash = false;
                    }
                }
                else {
                    // reached EOL
                    if (len == 0) {
                        isNewLine = true;
                        skipWhiteSpace = true;
                        len = 0;
                        continue;
                    }
                    if (inOff >= inLimit) {
                        inLimit = (inStream==null)
                                ?reader.read(inCharBuf)
                                :inStream.read(inByteBuf);
                        inOff = 0;
                        if (inLimit <= 0) {
                            if (precedingBackslash) {
                                len--;
                            }
                            return len;
                        }
                    }
                    if (precedingBackslash) {
                        len -= 1;
                        //skip the leading whitespace characters in following line
                        skipWhiteSpace = true;
                        appendedLineBegin = true;
                        precedingBackslash = false;
                        if (c == '\r') {
                            skipLF = true;
                        }
                    } else {
                        return len;
                    }
                }
            }
        }
    }

}
