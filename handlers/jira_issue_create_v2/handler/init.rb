# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class JiraIssueCreateV2
  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)

    # Retrieve all of the handler info values and store them in a hash variable named @info_values.
    @info_values = {}
    REXML::XPath.each(@input_document, "/handler/infos/info") do |item|
      @info_values[item.attributes["name"]] = item.text.to_s.strip
    end

    # Retrieve all of the handler parameters and store them in a hash variable named @parameters.
    @parameters = {}
    REXML::XPath.each(@input_document, "/handler/parameters/parameter") do |item|
      @parameters[item.attributes["name"]] = item.text.to_s.strip
    end

    @enable_debug_logging = @info_values['enable_debug_logging'].downcase == 'yes' ||
                            @info_values['enable_debug_logging'].downcase == 'true'
    puts "Parameters: #{@parameters.inspect}" if @enable_debug_logging
  end

  def execute
    error_handling = @parameters['error_handling']
    error_message = nil
    begin
      api_route = "#{@info_values['site']}/rest/api/latest/issue"

      puts "API ROUTE: #{api_route}" if @enable_debug_logging

      resource = RestClient::Resource.new(api_route, user: @info_values['username'], password: @info_values['password'])

      # Building the object that will be sent to Jira
      data = { fields:  {} }
      data[:fields].tap do |json|
        json[:project] = { 'key' => @parameters['project_key'] } if !@parameters['project_key'].empty?
        json[:issuetype] = { 'name' => @parameters['type'] } if !@parameters['type'].empty?
        json[:summary] = @parameters['summary'] if !@parameters['summary'].empty?
        json[:description] = @parameters['description'] if !@parameters['description'].empty?
        json[:assignee] = { 'name' => @parameters['assignee'] } if !@parameters['assignee'].empty?
        json[:priority] = { 'name' => @parameters['priority'] } if !@parameters['priority'].empty?
        json[:duedate] = @parameters['due_date'] if !@parameters['due_date'].empty?
      end

      if !@parameters['additional_fields_json'].empty?
        additional_fields = JSON.parse(@parameters['additional_fields_json'])
        data[:fields] = data[:fields].merge(additional_fields)
      end
      puts "DATA: #{data.to_json}" if @enable_debug_logging

      # Post to the API
      response = resource.post(data.to_json, accept: 'json', content_type: 'application/json')
      issue = JSON.parse(response.body)
      puts "ISSUE: #{issue.inspect}" if @enable_debug_logging

    rescue RestClient::ExceptionWithResponse => error
      puts error.inspect if @enable_debug_logging
      error_response = JSON.parse(error.response)
      server_errors = !error_response['errorMessages'].empty? ? error_response['errorMessages'].join(' | ') : nil
      errors = error_response['errors'].map { |k, v| "#{k}: #{v}" }.join(' | ')
      error_message = "#{error.http_code}: #{server_errors.nil? ? errors : server_errors}"
      raise error_message if error_handling == 'Raise Error'
    rescue Exception => error
      error_message = error.inspect
      raise error if error_handling == 'Raise Error'
    end

    <<-RESULTS
    <results>
      <result name="Handler Error Message">#{escape(error_message)}</result>
      <result name="id">#{error_message.nil? ? issue['id'] : ''}</result>
      <result name="key">#{error_message.nil? ? issue['key'] : ''}</result>
      <result name="url">#{error_message.nil? ? issue['self'] : ''}</result>
    </results>
    RESULTS
  end

  ##############################################################################
  # General handler utility functions
  ##############################################################################

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
  ESCAPE_CHARACTERS = { '&' => '&amp;', '>' => '&gt;', '<' => '&lt;', '"' => '&quot;' }.freeze
end
