/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.validator.routines;

import org.apache.commons.validator.GenericValidator;
import org.apache.commons.validator.util.Flags;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p><b>URL Validation</b> routines.</p>
 * Behavior of validation is modified by passing in options:
 * <li>ALLOW_2_SLASHES - [FALSE]  Allows double '/' characters in the path
 * component.</li>
 * <li>NO_FRAGMENT- [FALSE]  By default fragments are allowed, if this option is
 * included then fragments are flagged as illegal.</li>
 * <li>ALLOW_ALL_SCHEMES - [FALSE] By default only http, https, and ftp are
 * considered valid schemes.  Enabling this option will let any scheme pass validation.</li>
 * <li>MANUAL_AUTHORITY_VALIDATION - [FALSE] By default, URL authorities must be IANA-defined
 * domain names. Enabling this option and providing an array of authority validators
 * will validate the URL's authority against the regexes; if it matches, the authority
 * validation will succeed. If no match is found, domain name validation is used.</li>
 *
 * <p>Originally based in on php script by Debbie Dyer, validation.php v1.2b, Date: 03/07/02,
 * http://javascript.internet.com. However, this validation now bears little resemblance
 * to the php original.</p>
 * <pre>
 *   Example of usage:
 *   Construct a UrlValidator with valid schemes of "http", and "https".
 *
 *    String[] schemes = {"http","https"}.
 *    UrlValidator urlValidator = new UrlValidator(schemes);
 *    if (urlValidator.isValid("ftp://foo.bar.com/")) {
 *       System.out.println("url is valid");
 *    } else {
 *       System.out.println("url is invalid");
 *    }
 *
 *    prints "url is invalid"
 *   If instead the default constructor is used.
 *
 *    UrlValidator urlValidator = new UrlValidator();
 *    if (urlValidator.isValid("ftp://foo.bar.com/")) {
 *       System.out.println("url is valid");
 *    } else {
 *       System.out.println("url is invalid");
 *    }
 *
 *   prints out "url is valid"
 *  </pre>
 *
 * @see
 * <a href='http://www.ietf.org/rfc/rfc2396.txt' >
 *  Uniform Resource Identifiers (URI): Generic Syntax
 * </a>
 *
 * @version $Revision$ $Date$
 * @since Validator 1.4
 */
public class UrlValidator implements Serializable {

    /**
     * Allows all validly formatted schemes to pass validation instead of
     * supplying a set of valid schemes.
     */
    public static final int ALLOW_ALL_SCHEMES = 1 << 0;

    /**
     * Allow two slashes in the path component of the URL.
     */
    public static final int ALLOW_2_SLASHES = 1 << 1;

    /**
     * Enabling this options disallows any URL fragments.
     */
    public static final int NO_FRAGMENTS = 1 << 2;

    /**
     * If enabled, consults a provided list of RegexValidators when validating
     * the URL authority. This allows names like "localhost", "test-machine", etc.
     */
    public static final int MANUAL_AUTHORITY_VALIDATION = 1 << 3;

    // Drop numeric, and  "+-." for now
    private static final String AUTHORITY_CHARS_REGEX = "\\p{Alnum}\\-\\.";

    /**
     * This expression derived/taken from the BNF for URI (RFC2396).
     */
    private static final String URL_REGEX =
            "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
    //                                                                      12            3  4          5       6   7        8 9
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    /**
     * Schema/Protocol (ie. http:, ftp:, file:, etc).
     */
    private static final int PARSE_URL_SCHEME = 2;

    /**
     * Includes hostname/ip and port number.
     */
    private static final int PARSE_URL_AUTHORITY = 4;

    private static final int PARSE_URL_PATH = 5;

    private static final int PARSE_URL_QUERY = 7;

    private static final int PARSE_URL_FRAGMENT = 9;

    /**
     * Protocol (ie. http:, ftp:,https:).
     */
    private static final String SCHEME_REGEX = "^\\p{Alpha}[\\p{Alnum}\\+\\-\\.]*";
    private static final Pattern SCHEME_PATTERN = Pattern.compile(SCHEME_REGEX);

    private static final String AUTHORITY_REGEX =
            "^([" + AUTHORITY_CHARS_REGEX + "]*)(:\\d*)?(.*)?";
    //                                                                            1                          2  3       4
    private static final Pattern AUTHORITY_PATTERN = Pattern.compile(AUTHORITY_REGEX);

    private static final int PARSE_AUTHORITY_HOST_IP = 1;

    private static final int PARSE_AUTHORITY_PORT = 2;

    /**
     * Should always be empty.
     */
    private static final int PARSE_AUTHORITY_EXTRA = 3;

    private static final String PATH_REGEX = "^(/[-\\w:@&?=+,.!/~*'%$_;\\(\\)]*)?$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH_REGEX);

    private static final String QUERY_REGEX = "^(.*)$";
    private static final Pattern QUERY_PATTERN = Pattern.compile(QUERY_REGEX);

    private static final String LEGAL_ASCII_REGEX = "^\\p{ASCII}+$";
    private static final Pattern ASCII_PATTERN = Pattern.compile(LEGAL_ASCII_REGEX);

    private static final String PORT_REGEX = "^:(\\d{1,5})$";
    private static final Pattern PORT_PATTERN = Pattern.compile(PORT_REGEX);

    /**
     * Holds the set of current validation options.
     */
    private Flags options = null;

    /**
     * The set of schemes that are allowed to be in a URL.
     */
    private Set allowedSchemes = new HashSet();

    /**
     * Regular expressions used to manually validate authorities if IANA
     * domain name validation isn't desired.
     */
    private RegexValidator[] authorityValidators;

    /**
     * If no schemes are provided, default to this set.
     */
    protected String[] defaultSchemes = {"http", "https", "ftp"};

    /**
     * Singleton instance of this class with default schemes and options.
     */
    private static final UrlValidator DEFAULT_URL_VALIDATOR = new UrlValidator();

    /**
     * Returns the singleton instance of this class with default schemes and options.
     * @return singleton instance with default schemes and options
     */
    public static UrlValidator getInstance() {
        return DEFAULT_URL_VALIDATOR;    
    }

    /**
     * Create a UrlValidator with default properties.
     */
    public UrlValidator() {
        this(null);
    }

    /**
     * Behavior of validation is modified by passing in several strings options:
     * @param schemes Pass in one or more url schemes to consider valid, passing in
     *        a null will default to "http,https,ftp" being valid.
     *        If a non-null schemes is specified then all valid schemes must
     *        be specified. Setting the ALLOW_ALL_SCHEMES option will
     *        ignore the contents of schemes.
     */
    public UrlValidator(String[] schemes) {
        this(schemes, 0);
    }

    /**
     * Initialize a UrlValidator with the given validation options.
     * @param options The options should be set using the public constants declared in
     * this class.  To set multiple options you simply add them together.  For example,
     * ALLOW_2_SLASHES + NO_FRAGMENTS enables both of those options.
     */
    public UrlValidator(int options) {
        this(null, null, options);
    }

    /**
     * Behavior of validation is modified by passing in options:
     * @param schemes The set of valid schemes.
     * @param options The options should be set using the public constants declared in
     * this class.  To set multiple options you simply add them together.  For example,
     * ALLOW_2_SLASHES + NO_FRAGMENTS enables both of those options.
     */
    public UrlValidator(String[] schemes, int options) {
        this(schemes, null, options);
    }

    /**
     * Initialize a UrlValidator with the given validation options.
     * @param authorityValidators regexes used to validate the authority part;
     * see {@link #MANUAL_AUTHORITY_VALIDATION}
     * @param options Validation options. Set using the public constants of this class.
     * To set multiple options, simply add them together:
     * <p><code>ALLOW_2_SLASHES + NO_FRAGMENTS</code></p>
     * enables both of those options.
     */
    public UrlValidator(RegexValidator[] authorityValidators, int options) {
        this(null, authorityValidators, options);
    }

    /**
     * Customizable constructor. Validation behavior is modifed by passing in options.
     * @param schemes the set of valid schemes
     * @param authorityValidators the set of regexes to manually validate the scheme against
     * @param options Validation options. Set using the public constants of this class.
     * To set multiple options, simply add them together:
     * <p><code>ALLOW_2_SLASHES + NO_FRAGMENTS</code></p>
     * enables both of those options.
     */
    public UrlValidator(String[] schemes, RegexValidator[] authorityValidators, int options) {
        this.options = new Flags(options);

        if (this.options.isOn(ALLOW_ALL_SCHEMES)) {
            return;
        }

        if (schemes == null) {
            schemes = this.defaultSchemes;
        }

        if (this.options.isOn(MANUAL_AUTHORITY_VALIDATION)) {
            this.authorityValidators = authorityValidators;
        }

        this.allowedSchemes.addAll(Arrays.asList(schemes));
    }

    /**
     * <p>Checks if a field has a valid url address.</p>
     *
     * @param value The value validation is being performed on.  A <code>null</code>
     * value is considered invalid.
     * @return true if the url is valid.
     */
    public boolean isValid(String value) {
        if (value == null) {
            return false;
        }

        if (!ASCII_PATTERN.matcher(value).matches()) {
            return false;
        }

        // Check the whole url address structure
        Matcher urlMatcher = URL_PATTERN.matcher(value);
        if (!urlMatcher.matches()) {
            return false;
        }

        if (!isValidScheme(urlMatcher.group(PARSE_URL_SCHEME))) {
            return false;
        }

        if (!isValidAuthority(urlMatcher.group(PARSE_URL_AUTHORITY))) {
            return false;
        }

        if (!isValidPath(urlMatcher.group(PARSE_URL_PATH))) {
            return false;
        }

        if (!isValidQuery(urlMatcher.group(PARSE_URL_QUERY))) {
            return false;
        }

        if (!isValidFragment(urlMatcher.group(PARSE_URL_FRAGMENT))) {
            return false;
        }

        return true;
    }

    /**
     * Validate scheme. If schemes[] was initialized to a non null,
     * then only those scheme's are allowed.  Note this is slightly different
     * than for the constructor.
     * @param scheme The scheme to validate.  A <code>null</code> value is considered
     * invalid.
     * @return true if valid.
     */
    protected boolean isValidScheme(String scheme) {
        if (scheme == null) {
            return false;
        }

        if (!SCHEME_PATTERN.matcher(scheme).matches()) {
            return false;
        }

        if (this.options.isOff(ALLOW_ALL_SCHEMES)) {

            if (!this.allowedSchemes.contains(scheme)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if the authority is properly formatted.  An authority is the combination
     * of hostname and port.  A <code>null</code> authority value is considered invalid.
     * @param authority Authority value to validate.
     * @return true if authority (hostname and port) is valid.
     */
    protected boolean isValidAuthority(String authority) {
        if (authority == null) {
            return false;
        }

        // check manual authority validation if specified
        if (this.options.isOn(MANUAL_AUTHORITY_VALIDATION)) {
            if (authorityValidators == null) {
                throw new IllegalStateException(
                        "manual authority validation enabled, but no authority validators specified");
            }

            for (int i = 0; i < authorityValidators.length; i++) {
                if (authorityValidators[i].isValid(authority)) {
                    return true;
                }
            }
        }

        Matcher authorityMatcher = AUTHORITY_PATTERN.matcher(authority);
        if (!authorityMatcher.matches()) {
            return false;
        }

        String hostLocation = authorityMatcher.group(PARSE_AUTHORITY_HOST_IP);
        // check if authority is hostname or IP address:
        // try a hostname first since that's much more likely
        DomainValidator domainValidator = DomainValidator.getInstance();
        if (!domainValidator.isValid(hostLocation)) {
            // try an IP address
            InetAddressValidator inetAddressValidator =
                InetAddressValidator.getInstance();
            if (!inetAddressValidator.isValid(hostLocation)) {
                // isn't either one, so the URL is invalid
                return false;
            }
        }

        String port = authorityMatcher.group(PARSE_AUTHORITY_PORT);
        if (port != null) {
            if (!PORT_PATTERN.matcher(port).matches()) {
                return false;
            }
        }

        String extra = authorityMatcher.group(PARSE_AUTHORITY_EXTRA);
        if (!GenericValidator.isBlankOrNull(extra)) {
            return false;
        }

        return true;
    }

    /**
     * Returns true if the path is valid.  A <code>null</code> value is considered invalid.
     * @param path Path value to validate.
     * @return true if path is valid.
     */
    protected boolean isValidPath(String path) {
        if (path == null) {
            return false;
        }

        if (!PATH_PATTERN.matcher(path).matches()) {
            return false;
        }

        int slash2Count = countToken("//", path);
        if (this.options.isOff(ALLOW_2_SLASHES) && (slash2Count > 0)) {
            return false;
        }

        int slashCount = countToken("/", path);
        int dot2Count = countToken("..", path);
        if (dot2Count > 0) {
            if ((slashCount - slash2Count - 1) <= dot2Count) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if the query is null or it's a properly formatted query string.
     * @param query Query value to validate.
     * @return true if query is valid.
     */
    protected boolean isValidQuery(String query) {
        if (query == null) {
            return true;
        }

        return QUERY_PATTERN.matcher(query).matches();
    }

    /**
     * Returns true if the given fragment is null or fragments are allowed.
     * @param fragment Fragment value to validate.
     * @return true if fragment is valid.
     */
    protected boolean isValidFragment(String fragment) {
        if (fragment == null) {
            return true;
        }

        return this.options.isOff(NO_FRAGMENTS);
    }

    /**
     * Returns the number of times the token appears in the target.
     * @param token Token value to be counted.
     * @param target Target value to count tokens in.
     * @return the number of tokens.
     */
    protected int countToken(String token, String target) {
        int tokenIndex = 0;
        int count = 0;
        while (tokenIndex != -1) {
            tokenIndex = target.indexOf(token, tokenIndex);
            if (tokenIndex > -1) {
                tokenIndex++;
                count++;
            }
        }
        return count;
    }
}