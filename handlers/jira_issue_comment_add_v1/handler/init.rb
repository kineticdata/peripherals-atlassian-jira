# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class JiraIssueCommentAddV1
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
      api_route = "#{@info_values['site']}/rest/api/latest/issue/#{@parameters['issue_key']}/comment"

      puts "API ROUTE: #{api_route}" if @enable_debug_logging

      resource = RestClient::Resource.new(api_route, user: @info_values['username'], password: @info_values['password'])

      # Post to the API
      response = resource.post(@parameters['api_body'], accept: 'json', content_type: 'application/json')

      comment = JSON.parse(response.body)
      puts "COMMENT: #{comment.inspect}" if @enable_debug_logging

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
      <result name="id">#{error_message.nil? ? comment['id'] : ''}</result>
      <result name="url">#{error_message.nil? ? comment['self'] : ''}</result>
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
