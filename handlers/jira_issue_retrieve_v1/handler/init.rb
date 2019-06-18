# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class JiraIssueRetrieveV1
  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)

    # Store the info values in a Hash of info names to values.
    @info_values = {}
    REXML::XPath.each(@input_document,"/handler/infos/info") { |item|
      @info_values[item.attributes['name']] = item.text
    }
    @enable_debug_logging = @info_values['enable_debug_logging'] == 'Yes'

    # Store parameters values in a Hash of parameter names to values.
    @parameters = {}
    REXML::XPath.match(@input_document, '/handler/parameters/parameter').each do |node|
      @parameters[node.attribute('name').value] = node.text.to_s
    end
  end

  def execute()
    if @info_values['site'].match(/http:\/\//)
      puts "WARNING: The site '#{@info_values['site']}' does not appear to be configured \
for ssl. This handler uses basic authentication which means your username and password \
are being sent over an insecure connection if ssl is not configured."
    end

    resource = RestClient::Resource.new(@info_values['site'], :user => @info_values['username'], :password => @info_values['password'])
    resp = resource['rest/api/2/issue'][@parameters['issue_key']].get :content_type => "application/json"
    
    
    puts "Jira Response" if @enable_debug_logging
    puts resp.code if @enable_debug_logging
    puts resp.body if @enable_debug_logging

    data = JSON.parse(resp.body)

    puts data

    return_xml = "<results>\n"

    # Simple Fields
    [ 'id','key' ].each do |field|
      return_xml += "<result name='#{escape(field)}'>#{escape(data[field])}</result>\n"
    end

    # Array Fields (to CSV)
    # None

    # Depth 2 Fields
    [ 'summary','description' ].each do |field|
      return_xml += "<result name='#{escape(field)}'>#{escape(data['fields'][field])}</result>\n"
    end

    # creator
    [ 'name','emailAddress','displayName' ].each do |field|
      return_xml += "<result name='creator_#{escape(field)}'>#{escape(data['fields']['creator'][field])}</result>\n"
      # bindings['jira_issue']["creator_"+field] = data['issue']['fields']['creator'][field]
    end

    # project
    [ 'key','name' ].each do |field|
      return_xml += "<result name='project_#{escape(field)}'>#{escape(data['fields']['project'][field])}</result>\n"
      # bindings['jira_issue']["project_"+field] = data['issue']['fields']['project'][field]
    end

    # status
    [ 'name' ].each do |field|
      return_xml += "<result name='status_#{escape(field)}'>#{escape(data['fields']['status'][field])}</result>\n"
      # bindings['jira_issue']["status_"+field] = data['issue']['fields']['status'][field]
    end

    # issue type
    [ 'name' ].each do |field|
      return_xml += "<result name='issuetype_#{escape(field)}'>#{escape(data['fields']['issuetype'][field])}</result>\n"
      # bindings['jira_issue']["issuetype_"+field] = data['issue']['fields']['issuetype'][field]
    end

    return_xml += "</results>"

    puts "Return XML" if @enable_debug_logging
    puts return_xml if @enable_debug_logging

    return return_xml
  end

  # This is a template method that is used to escape results values (returned in
  # execute) that would cause the XML to be invalid.  This method is not
  # necessary if values do not contain character that have special meaning in
  # XML (&, ", <, and >), however it is a good practice to use it for all return
  # variable results in case the value could include one of those characters in
  # the future.  This method can be copied and reused between handlers.
  def escape(string)
    # Globally replace characters based on the ESCAPE_CHARACTERS constant
    string.to_s.gsub(/[&"><]/) { |special| ESCAPE_CHARACTERS[special] } if string
  end
  # This is a ruby constant that is used by the escape method
  ESCAPE_CHARACTERS = {'&'=>'&amp;', '>'=>'&gt;', '<'=>'&lt;', '"' => '&quot;'}

end