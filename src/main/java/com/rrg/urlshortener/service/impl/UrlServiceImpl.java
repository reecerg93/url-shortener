package com.rrg.urlshortener.service.impl;

import com.rrg.urlshortener.exception.InvalidFieldException;
import com.rrg.urlshortener.exception.MissingFieldException;
import com.rrg.urlshortener.exception.ResourceNotFoundException;
import com.rrg.urlshortener.exception.ShortUrlIdGenerationException;
import com.rrg.urlshortener.model.Url;
import com.rrg.urlshortener.repository.UrlRepository;
import com.rrg.urlshortener.service.UrlService;
import com.rrg.urlshortener.util.UrlUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

@Slf4j
@Service
@Qualifier("urlService")
public class UrlServiceImpl implements UrlService {

    private static final String URL_FORMAT_ERROR = "%s isn't a valid URL, it needs a protocol, domain and TLD";

    @Value("${attempt.limit}")
    private int attemptLimit;

    private final UrlUtil util;
    private final UrlRepository repo;

    public UrlServiceImpl(UrlUtil util, UrlRepository repo) {
        this.util = util;
        this.repo = repo;
    }

    @Override
    public Url createShortUrl(String fullUrl) {
        if (StringUtils.isBlank(fullUrl)) {
            throw new MissingFieldException("URL isn't provided");
        }
        if (util.isValidUrl(fullUrl)) {
            var shortUrlId = StringUtils.EMPTY;
            var attempt = 1;
            while (attempt <= attemptLimit) {
                shortUrlId = util.generateId();
                if (repo.findByShortUrlId(shortUrlId).isEmpty()) {
                    break;
                } else {
                    shortUrlId = StringUtils.EMPTY;
                    attempt++;
                }
            }
            if (StringUtils.EMPTY.equals(shortUrlId)) {
                throw new ShortUrlIdGenerationException("Couldn't generate Short URL ID");
            }
            return saveUrl(fullUrl, shortUrlId);
        }
        throw new InvalidFieldException(String.format(URL_FORMAT_ERROR, fullUrl));
    }

    @Override
    public HttpHeaders processRedirection(String shortUrlId) {
        var url = getUrlByShortUrlId(shortUrlId);
        try {
            var uri = new URL(url.getFullUrl()).toURI();
            var headers = new HttpHeaders();
            headers.setLocation(uri);
            incrementVisits(url);
            return headers;
        } catch (URISyntaxException | MalformedURLException e) {
            log.error("{} is persisted but isn't a valid URL", url.getFullUrl(), e);
            throw new InvalidFieldException(String.format(URL_FORMAT_ERROR, url.getFullUrl()));
        }
    }

    @Override
    public Url getUrlByShortUrlId(String shortUrlId) {
        if (StringUtils.isBlank(shortUrlId)) {
            throw new MissingFieldException("Short URL ID isn't provided");
        }
        return repo.findByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("No resource found for %s", shortUrlId)));
    }

    @Override
    public List<Url> getUrlsByFullUrl(String fullUrl) {
        if (StringUtils.isBlank(fullUrl)) {
            throw new MissingFieldException("Full Url isn't provided");
        }
        fullUrl = util.sanitiseUrl(fullUrl);
        if (util.isValidUrl(fullUrl)) {
            return repo.findAllByFullUrl(fullUrl);
        }
        throw new InvalidFieldException(String.format(URL_FORMAT_ERROR, fullUrl));
    }

    @Override
    public Url saveUrl(String fullUrl, String shortUrlId) {
        var url = new Url();
        url.setFullUrl(util.sanitiseUrl(fullUrl));
        url.setShortUrlId(shortUrlId);
        return repo.save(url);
    }

    @Override
    public void incrementVisits(Url url) {
        url.setVisits(url.getVisits() + 1);
        repo.save(url);
    }
}
