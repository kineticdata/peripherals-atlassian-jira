RESOURCE_PATH = File.expand_path(File.join(File.dirname(__FILE__), 'resources'))

# Load the ruby Mime Types library unless it has already been loaded.  This prevents
# multiple handlers using the same library from causing problems.
if not defined?(MIME)
  # Calculate the location of this file
  handler_path = File.expand_path(File.dirname(__FILE__))
  # Calculate the location of our library and add it to the Ruby load path
  library_path = File.join(handler_path, 'vendor/mime-types-1.19/lib/')
  $:.unshift library_path
  # Require the library
  require 'mime/types'
end

# Validate the the loaded Mime Types library is the library that is expected for
# this handler to execute properly.
if not defined?(MIME::Types::VERSION)
  raise "The Mime class does not define the expected VERSION constant."
elsif MIME::Types::VERSION != '1.19'
  raise "Incompatible library version #{MIME::Types::VERSION} for Mime Types.  Expecting version 1.19."
end




# Load the ruby Rest Client library unless it has already been loaded.  This prevents
# multiple handlers using the same library from causing problems.
if not defined?(RestClient)
  # Calculate the location of this file
  handler_path = File.expand_path(File.dirname(__FILE__))
  # Calculate the location of our library and add it to the Ruby load path
  library_path = File.join(handler_path, 'vendor/rest-client-1.6.7/lib/')
  $:.unshift library_path
  # Require the library
  require 'restclient'
end

# Validate the the loaded Rest Client library is the library that is expected for
# this handler to execute properly.
if not defined?(RestClient.version)
  raise "The Rest Client class does not define the expected VERSION constant."
elsif RestClient.version != '1.6.7'
  raise "Incompatible library version #{RestClient.version} for Rest Client.  Expecting version 1.6.7."
end
