require 'rubygems'
require 'sass'

module Sass
  module Importers
    class Proxy < Base
      def initialize(delegate)
        @delegate = delegate
      end

      def find_relative(uri, base, options)
        find(base.split('/').reverse.drop(1).reverse.join('/')+'/'+uri,options)
      end

      def extensions
        {'sass' => :sass, 'scss' => :scss}
      end

      def find(uri, options)
        ext = _ext(uri)
        if ext
          contents = @delegate.find(uri)
          options[:syntax] = extensions[ext]
          options[:filename] = uri
          contents && Sass::Engine.new(contents, options)
        else
          extensions.map do |ext,syntax|
            name = "#{uri}.#{ext}"
            contents = @delegate.find(name)
            if contents
              options[:syntax] = syntax
              options[:filename] = name
              return Sass::Engine.new(contents, options)
            end
          end
          extensions.map do |ext,syntax|
            name = "#{_dir(uri)}_#{_name(uri)}.#{ext}"
            contents = @delegate.find(name)
            if contents
              options[:syntax] = syntax
              options[:filename] = name
              return Sass::Engine.new(contents, options)
            end
          end
          return nil
        end
      end

      def mtime(uri, options)
        @delegate.mtime(uri)
      end

      def key(uri, options)
        ["proxy", uri]
      end

      def to_s
        "Proxy"
      end

      private

      def _dir(uri)
        lastSep = uri.rindex('/')
        lastSep && uri[0..lastSep]
      end

      def _name(uri)
        lastSep = uri.rindex('/')
        if lastSep
          lastDot = uri[lastSep + 1..-1].rindex('.')
          if lastDot
            lastDot += lastSep + 1
          end
        else
          lastDot = uri.rindex('.');
        end
        return uri[(lastSep || -1)+1..(lastDot || 0)-1]
      end

      def _ext(uri)
        lastSep = uri.rindex('/')
        if lastSep
          lastDot = uri[lastSep + 1..-1].rindex('.')
          if lastDot
            lastDot += lastSep + 1
          end
        else
          lastDot = uri.rindex('.');
        end
        return lastDot && uri[lastDot+1..-1]
      end

    end
  end
end

options={
    :importer => Sass::Importers::Proxy.new(filesystem),
    :filename => filename,
    :cache => false
}
options[:importer].find(filename,options).render
