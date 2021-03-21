# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class JiraIssueAttachmentUploadV2
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
    # Use the create_attachment_from_... helper functions to retrieve the attachment content.
    attachment = create_attachment_from_json(@parameters['attachment_json']) if @parameters['attachment_input_type']=="JSON"
    attachment = create_attachment_from_field(@parameters['attachment_field'], @info_values['api_location']) if @parameters['attachment_input_type']=="Field"


    if @info_values['site'].match(/http:\/\//)
      puts "WARNING: The site '#{@info_values['site']}' does not appear to be configured \
for ssl. This handler uses basic authentication which means your username and password \
are being sent over an insecure connection if ssl is not configured."
    end

    site = "#{@info_values['site']}/rest/api/2/issue/#{@parameters['issue_key']}/attachments"

    begin
      File.open(attachment['name'],"w+") do |file|
        file.write(attachment['content']);
        file.rewind

        request = RestClient::Request.new(:method => :post, :url => site,
          :user => @info_values['username'], :password => @info_values['password'],
          :payload => {:multipart => true, :file => file}, :headers => {"X-Atlassian-Token" => "nocheck"})
        resp = request.execute

        results = JSON.parse(resp.body)
        puts "Jira Response: " + results.to_s if @enable_debug_logging
      end
    rescue Exception => error
      File.delete(attachment['name'])
      raise StandardError, error.inspect
    end

    puts "Deleting File after successful call" if @enable_debug_logging
    File.delete(attachment['name'])

    <<-RESULTS
    <results/>
    RESULTS
  end

  ##############################################################################
  # File Retrieve utility functions
  ##############################################################################
  def create_attachment_from_json(json)
    field_values = JSON.parse(json)
    puts("Using File URL: \n#{field_values[0]["url"]}") if @debug_logging_enabled
    puts("Using File Name: \n#{field_values[0]["name"]}") if @debug_logging_enabled
    attachment = RestClient::Resource.new(
      field_values[0]["url"],
      user: @info_values['api_username'],
      password: @info_values['api_password']
    ).get

    attachment_field = {}
    attachment_field['name'] = field_values[0]["name"]
    attachment_field['content'] = attachment.body

    return attachment_field
  end

  def create_attachment_from_field(field_name, server)
    # Call the Kinetic Request CE API
    begin
      # Submission API Route including Values
      # /{spaceSlug}/app/api/v1/submissions/{submissionId}}?include=...

      submission_api_route = server + '/app/api/v1' +
        '/submissions/' + URI.escape(@parameters['submission_id']) + '/?include=values'
      puts("Submission API Route: \n#{submission_api_route}") if @debug_logging_enabled

      # Retrieve the Submission Values
      submission_result = RestClient::Resource.new(
        submission_api_route,
        user: @info_values['api_username'],
        password: @info_values['api_password']
      ).get

      # If the submission exists
      unless submission_result.nil?

       submission = JSON.parse(submission_result)['submission']
        field_value = submission['values'][field_name]
        # If the attachment field value exists
        unless field_value.nil?
          files = []
          # Attachment field values are stored as arrays, one map for each file attachment
          field_value.each_index do |index|
            file_info = field_value[index]
            # The attachment file name is stored in the 'name' property
            # API route to get the generated attachment download link from Kinetic Request CE.
            attachment_download_api_route = server + '/app/api/v1' +
              '/submissions/' + URI.escape(@parameters['submission_id']) +
              '/files/' + URI.escape(field_name) +
              '/' + index.to_s +
              '/' + URI.escape(file_info['name']) +
              '/url'
            puts("Attachment Download API Route: \n#{attachment_download_api_route}") if @debug_logging_enabled


            # Retrieve the URL to download the attachment from Kinetic Request CE.
            # This URL will only be valid for a short amount of time before it expires
            # (usually about 5 seconds).
            attachment_download_result = RestClient::Resource.new(
              attachment_download_api_route,
              user: @info_values['api_username'],
              password: @info_values['api_password']
            ).get

            unless attachment_download_result.nil?
              url = JSON.parse(attachment_download_result)['url']
              file_info["url"] = url
            end
            file_info.delete("link")
            files << file_info
          end
        end
      end

    # If the credentials are invalid
    rescue RestClient::Unauthorized
      raise StandardError, "(Unauthorized): You are not authorized."
    rescue RestClient::ResourceNotFound => error
      raise StandardError, error.response
    end

    if files.nil?
        puts("No File in Field: \n#{field_name}") if @debug_logging_enabled
        return nil
    else
        puts("Using File URL: \n#{files[0]["url"]}") if @debug_logging_enabled
        puts("Using File Name: \n#{files[0]["name"]}") if @debug_logging_enabled
        attachment = RestClient::Resource.new(
          files[0]["url"],
          user: @info_values['api_username'],
          password: @info_values['api_password']
        ).get

        attachment_field = {}
        attachment_field['name'] = files[0]["name"]
        attachment_field['content'] = attachment.body

        return attachment_field
    end
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
